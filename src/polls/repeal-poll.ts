import {
	APIBaseInteraction,
	APIInteractionResponseChannelMessageWithSource,
	InteractionResponseType,
	MessageFlags,
	RESTPatchAPIChannelMessageJSONBody,
} from 'discord-api-types/v10';
import { AMENDMENT_CACHE_TIME, api } from '../utils';
import { BasePoll } from './poll';
import { escapeMarkdown } from '@discordjs/formatters';
import { globalState } from '..';

export default class RepealPoll extends BasePoll<RepealPoll> {
	type = "Repeal";
	amendment: number;

	constructor(amendment: number, amendmentText: string) {
		super(0.5, 3, 43200000, 'Repeal ' + escapeMarkdown(amendmentText));
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
			globalState.amendmentCache.set(this.amendment - 1, {
				text: newAmendment,
				expiry: Date.now() + AMENDMENT_CACHE_TIME,
			});
		});
	}

	async handle(interaction: APIBaseInteraction<any, any>): Promise<any> {
		const number = interaction.data.options[0].value;
		// Bounds check
		if (number < 1 || number > globalState.getTotalAmendments()) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `Enter a number between 1 - ${globalState.getTotalAmendments()}`,
				},
			} as APIInteractionResponseChannelMessageWithSource;
		}
		// Number to 0-based index
		let raw = await globalState.getAmendmentText(number - 1);
		const repealed = raw.startsWith('~~') && raw.endsWith('~~');

		if (repealed) {
			raw += ' this amendment is repealed!';
		}

		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				content: 'Poll added! i think',
			},
		};
	}
}
