import { globalState } from '..';
import BasePoll from './poll';

export default class ImpeachPoll extends BasePoll {
	reason: string;

	constructor(reason: string) {
		super('impeach', 0.75, 5, 604800000, '');
		this.reason = reason;
	}

	// Unique to this poll because we only want to fetch the president name when it's about to be sent
	async firePoll() {
		this.question = `Impeach ${(await globalState.getPresidentDiscordMember()).user.username}? ${this.reason}`;
		return super.firePoll();
	}

	// Don't allow 2 impeach polls
	isDuplicate(_sample: ImpeachPoll): boolean {
		return true;
	}

	async pollPassed() {
		return globalState.impeach();
	}
}
