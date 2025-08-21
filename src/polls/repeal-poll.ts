import { MessageFlags, RESTPatchAPIChannelMessageJSONBody } from 'discord-api-types/v10';
import { api } from '../utils';
import { globalState } from '..';
import BasePoll from './poll';
import { escapeMarkdown } from '@discordjs/formatters';

export default class RepealPoll extends BasePoll {
	// This is the amendment number, NOT its 0-based index
	amendment: number;

	constructor(amendment: number) {
		// We have to escape the markdown here (embeds dont render markdown in the title)
		super('repeal', 0.5, 3, 43200000);
		this.amendment = amendment;
	}

	async firePoll() {
		this.question = `Repeal ${escapeMarkdown(globalState.getAmendmentText(this.amendment))}`;
		return super.firePoll();
	}

	isDuplicate(sample: RepealPoll): boolean {
		return sample.amendment == this.amendment;
	}

	async pollPassed() {
		// Update server data
		let amendment = globalState.serverData.amendments[this.amendment - 1];
		// Flip repealed flag
		amendment.repealed = !amendment.repealed;
		// Assign to OG slot (... do I need to do this?)
		globalState.serverData.amendments[this.amendment - 1] = amendment;
		// Get the amendment to edit
		const raw = amendment.content;
		// Edit the message
		return api(`channels/${globalState.serverData.amendments}/messages/${amendment.id}`, {
			method: 'PATCH',
			body: {
				// This adds the squiggles for strikethrough if it's repealed
				content: globalState.getAmendmentText(this.amendment),
				// if repealed, don't let gifs and shit stay in the amendment
				flags: amendment.repealed ? MessageFlags.SuppressEmbeds : undefined,
			} as RESTPatchAPIChannelMessageJSONBody,
		});
	}
}
