import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['tests/**/*.test.js'],
    environment: 'node',
    reporters: ['default'],
    // The benchmark file weaves several large cloths; five seconds is not
    // enough for it on a machine that is also running the rest of the suite.
    testTimeout: 30000,
    hookTimeout: 30000,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.js'],
      reporter: ['text-summary', 'lcov'],
    },
  },
});
