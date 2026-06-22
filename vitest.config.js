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
      // The floors sit a little under what the suite currently reaches, so a
      // change that drops coverage by a line does not fail the build while a
      // change that drops a whole module does. Raise them when the measured
      // figure rises, never lower them to make a build pass.
      thresholds: {
        statements: 97,
        branches: 92,
        functions: 98,
        lines: 97,
      },
    },
  },
});
