import { MessageFlags, RESTPatchAPIChannelMessageJSONBody } from 'discord-api-types/v10';
import { AMENDMENT_CACHE_TIME, api } from '../utils';
import { globalState } from '..';
import { Amendment } from '../types';
import BasePoll from './poll';

export default class RepealPoll extends BasePoll {
	amendment: number;

	constructor(amendment: number, amendmentText: string) {
		super('repeal', 0.5, 3, 43200000, 'Repeal ' + amendmentText);
		this.amendment = amendment;
	}

	isDuplicate(sample: RepealPoll): boolean {
		return sample.amendment == this.amendment;
	}

	async pollPassed() {
		// Get the amendment to edit
		const raw = await globalState.getAmendmentText(this.amendment - 1);
		const repealed = raw.startsWith('~~') && raw.endsWith('~~');
		const newAmendment = repealed ? raw.substring(2, raw.length - 2) : '~~' + raw + '~~';
		// Edit the message
		return api(`channels/${globalState.serverData.amendments}/messages/${globalState.serverData.amendmentIDs[this.amendment - 1]}`, {
			method: 'PATCH',
			body: {
				// if it's already repealed, remove the squiggles. Otherwise add them
				content: newAmendment,
				// if repealed, don't let gifs and shit stay in the amendment
				flags: repealed ? MessageFlags.SuppressEmbeds : undefined,
			} as RESTPatchAPIChannelMessageJSONBody,
		}).then(() => {
			// Update amendment cache
			globalState.serverData.amendmentCache.set(this.amendment - 1, new Amendment(newAmendment, Date.now() + AMENDMENT_CACHE_TIME));
		});
	}
}
