declare global {
  interface Window {
    __API_BASE_URL__?: string;
  }
}

/**
 * Browser-reachable API base URL.
 *
 * Empty in development, where Vite proxies the domain paths to the gateway. A deployed container renders
 * `config.js` from `COURSES_API_BASE_URL` at startup, before the bundle loads.
 */
export const apiBaseUrl: string = window.__API_BASE_URL__ ?? '';
