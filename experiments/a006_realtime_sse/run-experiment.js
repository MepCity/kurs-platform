import { runScenario } from './lib/scenario.js';

const result = await runScenario();
process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
