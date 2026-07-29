(function() {
    'use strict';

    const styleId = 'samtch-early-hider';
    if (document.getElementById(styleId)) return;

    console.log('[Samtch] early_hider.js active');

    const style = document.createElement('style');
    style.id = styleId;
    style.textContent = `
        /* Permanent Hiding */
        .stream-info-social-panel,
        .stream-info-card,
        .disclosure-card,
        .tw-upsell-banner {
            display: none !important;
        }

        /* Initial Hiding for Controls (Fade-in later) */
        .video-player__controls,
        [data-a-target="player-controls"],
        .player-controls__right-control-group,
        .player-controls__left-control-group {
            opacity: 0 !important;
            pointer-events: none !important;
            transition: opacity 0.4s ease-in-out !important;
        }

        /* Reveal when Samtch signals ready */
        html.samtch-ready .video-player__controls,
        html.samtch-ready [data-a-target="player-controls"],
        html.samtch-ready .player-controls__right-control-group,
        html.samtch-ready .player-controls__left-control-group {
            opacity: 1 !important;
            pointer-events: auto !important;
        }
    `;
    document.documentElement.appendChild(style);

    // Safety timeout: Always reveal after 10 seconds in case injection fails
    setTimeout(() => {
        if (!document.documentElement.classList.contains('samtch-ready')) {
            console.warn('[Samtch] Safety timeout: forcing control visibility');
            document.documentElement.classList.add('samtch-ready');
        }
    }, 10000);
})();
