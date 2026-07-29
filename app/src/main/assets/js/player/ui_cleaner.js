(function() {
    'use strict';

    // Self-cleanup: Clear existing intervals and observers from previous runs
    if (window.samtch_cleaner_init_int) clearInterval(window.samtch_cleaner_init_int);
    if (window.samtch_cleaner_maint_int) clearInterval(window.samtch_cleaner_maint_int);
    if (window.samtch_cleaner_obs) window.samtch_cleaner_obs.disconnect();

    console.log('[Samtch] ui_cleaner.js starting fresh session...');

    function injectStyles() {
        const styleId = 'samtch-player-cleaner';
        if (document.getElementById(styleId)) return;

        const style = document.createElement('style');
        style.id = styleId;
        style.textContent = `
            /* Hide distracting elements */
            .stream-info-card,
            [data-a-target="player-fullscreen-button"],
            [data-a-target="player-clip-button"],
            [data-a-target="player-forward-button"],
            [data-a-target="player-rewind-button"],
            [data-a-target="player-theatre-mode-button"],
            button[aria-label*="Clip"],
            button[title*="Clip"],
            .stream-info-social-panel,
            .samtch-hidden-button,
            .ad-banner,
            .disclosure-card,
            .tw-upsell-banner {
                display: none !important;
            }
        `;
        document.head.appendChild(style);
    }

    function clean() {
        const playerExists = document.querySelector('.video-player') ||
                           document.querySelector('[data-a-target="video-player"]');

        if (!playerExists) return;

        injectStyles();

        // Remove "Watch on Twitch" button manually if it persists
        document.querySelectorAll('.tw-svg').forEach(container => {
            const svg = container.querySelector('svg');
            if (svg && svg.getAttribute('viewBox') === '0 0 65 16') {
                const button = container.closest('button');
                if (button) button.remove();
            }
        });

        // Remove Clip buttons by label text
        document.querySelectorAll('[data-a-target="tw-core-button-label-text"]').forEach(el => {
            if (el.textContent.trim() === 'Clip') {
                const button = el.closest('button');
                if (button) {
                    console.log('[Samtch] Removing clip button via label text');
                    button.remove();
                }
            }
        });
    }

    // High frequency cleanup during initial load
    const startTime = Date.now();
    window.samtch_cleaner_init_int = setInterval(() => {
        clean();
        if (Date.now() - startTime > 8000) {
            clearInterval(window.samtch_cleaner_init_int);
            // Switch to maintenance cleaning
            window.samtch_cleaner_maint_int = setInterval(clean, 2500);
        }
    }, 500);

    window.samtch_cleaner_obs = new MutationObserver(clean);
    window.samtch_cleaner_obs.observe(document.documentElement, { childList: true, subtree: true });

    clean();
})();
