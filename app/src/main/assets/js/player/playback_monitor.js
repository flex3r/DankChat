(function() {
    'use strict';

    if (window.samtch_playback_monitor_int) clearInterval(window.samtch_playback_monitor_int);

    console.log('[Samtch] playback_monitor.js starting...');

    let signalSent = false;
    let playingStartTime = null;
    let monitorStartTime = Date.now();
    let lastStatusUpdate = 0;

    function reportStatus(message) {
        if (window.TwitchPlayerBridge && window.TwitchPlayerBridge.onLoadingStatus) {
            // Use localized strings if available
            let localizedMessage = message;
            if (window.SamtchStrings) {
                if (message === 'Initializing player components...') localizedMessage = window.SamtchStrings.initializing_player;
                else if (message === 'Searching for video stream...') localizedMessage = window.SamtchStrings.searching_video;
                else if (message === 'Preparing playback...') localizedMessage = window.SamtchStrings.preparing_playback;
            }
            window.TwitchPlayerBridge.onLoadingStatus(localizedMessage);
        }
    }

    function findAllVideos(root = document) {
        let videos = Array.from(root.querySelectorAll('video'));

        // Search in Shadow DOMs
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT, null, false);
        let node = walker.nextNode();
        while (node) {
            if (node.shadowRoot) {
                videos.push(...findAllVideos(node.shadowRoot));
            }
            node = walker.nextNode();
        }

        // Search in same-origin iframes
        if (root === document) {
            try {
                const iframes = document.querySelectorAll('iframe');
                iframes.forEach(iframe => {
                    try {
                        if (iframe.contentDocument) {
                            videos.push(...findAllVideos(iframe.contentDocument));
                        }
                    } catch (e) {}
                });
            } catch (e) {}
        }

        return videos;
    }

    function checkPlayback() {
        if (signalSent) return;

        const now = Date.now();
        const elapsed = now - monitorStartTime;
        const videos = findAllVideos();

        // Periodic status reporting during loading
        if (now - lastStatusUpdate > 1500) {
            lastStatusUpdate = now;
            if (videos.length === 0) {
                if (elapsed > 5000) {
                    reportStatus('Initializing player components...');
                } else if (elapsed > 1500) {
                    reportStatus('Searching for video stream...');
                }
            } else {
                // We have a video element, but it's not "playing" according to our checks
                reportStatus('Preparing playback...');
            }
        }

        if (videos.length === 0) return;

        for (const video of videos) {
            // Check if video is actually playing
            // readyState 2 (HAVE_CURRENT_DATA) is often enough to start hearing/seeing something
            const isPlaying = !video.paused && !video.ended && video.readyState >= 2;

            if (isPlaying) {
                if (!playingStartTime) playingStartTime = Date.now();

                const isAudible = !video.muted && video.volume > 0;
                const timeSinceStart = Date.now() - playingStartTime;

                // Trigger if:
                // 1. Sound is detected
                // 2. Or video has been playing for > 2 seconds even if muted (fallback)
                if (isAudible || timeSinceStart > 2000) {
                    if (window.TwitchPlayerBridge && window.TwitchPlayerBridge.onPlaybackStarted) {
                        console.log(`[Samtch] Playback detected (Audible: ${isAudible}, Fallback: ${timeSinceStart > 2000}). Signaling bridge.`);
                        window.TwitchPlayerBridge.onPlaybackStarted();
                        signalSent = true;
                        clearInterval(window.samtch_playback_monitor_int);
                        break;
                    }
                }
            }
        }
    }

    // High frequency polling initially
    window.samtch_playback_monitor_int = setInterval(checkPlayback, 200);
})();
