import '@testing-library/jest-dom';

// vitest 4 + jsdom 29 leaves window.localStorage / sessionStorage as a bare
// {} stub (the `--localstorage-file` Node warning at startup is the symptom).
// Install a minimal in-memory Storage shim so production code that touches
// localStorage at module load time (e.g. zustand stores reading initial state)
// works under jsdom.
function createMemoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear() {
      map.clear();
    },
    getItem(key: string) {
      return map.has(key) ? map.get(key)! : null;
    },
    key(index: number) {
      return Array.from(map.keys())[index] ?? null;
    },
    removeItem(key: string) {
      map.delete(key);
    },
    setItem(key: string, value: string) {
      map.set(key, String(value));
    },
  } as Storage;
}

function ensureStorage(name: 'localStorage' | 'sessionStorage') {
  const current = (window as any)[name];
  if (!current || typeof current.getItem !== 'function') {
    Object.defineProperty(window, name, {
      configurable: true,
      value: createMemoryStorage(),
    });
  }
}

ensureStorage('localStorage');
ensureStorage('sessionStorage');
