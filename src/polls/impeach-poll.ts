import { globalState } from '..';
import BasePoll from './poll';
import { InteractionResponseType } from 'discord-interactions';

export default class ImpeachPoll extends BasePoll {
	reason: string;

	constructor(reason: string) {
		super('impeach', 0.75, 5, 604800000);
		this.reason = reason;
	}

	// Unique to this poll because we only want to fetch the president name when it's about to be sent
	async firePoll() {
		try {
			const president = await globalState.getPresidentDiscordMember();
			this.question = `Impeach ${president.user.username}? ${this.reason}`;
			return super.firePoll();
		} catch (e) {
			console.error('Unable to find president! They must be gone', e);
			return {
				type: InteractionResponseType.CHANNEL_MESSAGE_WITH_SOURCE,
				data: {
					content: `Looks like the President left the server anyways, congrats! A new election will open soon.`,
				},
			};
		}
	}

	// Don't allow 2 impeach polls
	isDuplicate(_sample: ImpeachPoll): boolean {
		return true;
	}

	async pollPassed() {
		return globalState.impeach();
	}
}
