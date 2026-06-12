import './draw.js';
import './shop.js';

/**
 * The commands the tool offers.
 *
 * This file used to hold all nine of them and everything that reads the
 * register besides. It now does one thing: it loads the files that define the
 * commands, so that importing it is enough to have them, and re-exports the
 * register for everything that was already reading it here.
 *
 * The two imports above look unused and are not. Each one calls define as it
 * loads, which is what puts the commands in the register.
 */

export { DRAW_OPTIONS, buildDraft } from './draw.js';
export { listCommands, commandByName, commandUsage, toolUsage, specOf } from './registry.js';
