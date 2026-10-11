/// <reference types="vitest" />
import { defineConfig } from 'vite';
import preact from '@preact/preset-vite';

// base './': Die App läuft auf GitHub Pages unter einem Unterpfad (nutzer.github.io/repo/).
export default defineConfig({
  base: './',
  plugins: [preact()],
  build: { target: 'es2020' },
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.ts']
  }
});
