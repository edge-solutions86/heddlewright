import { run } from '../src/cli/run.js';

const result = run(process.argv.slice(2));
if (result.out !== '') console.log(result.out);
if (result.err !== '') console.error(result.err);
process.exitCode = result.code;
