import {
	APIBaseInteraction,
	APIInteractionResponseChannelMessageWithSource,
	InteractionResponseType,
	MessageFlags,
} from 'discord-api-types/v10';
import { globalState } from '..';
import { BaseCommand } from '../types';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>): Promise<any> {
		const number = interaction.data.options[0].value;
		// Bounds check
		if (number < 1 || number > globalState.getTotalAmendments()) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Enter a number between 1 - ${globalState.getTotalAmendments()}`,
				},
			} as APIInteractionResponseChannelMessageWithSource;
		}

		let raw = globalState.getAmendmentText(number);
		const repealed = raw.startsWith('~~') && raw.endsWith('~~');

		if (repealed) {
			raw += ' this amendment is repealed!';
		}

		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				content: raw,
			},
		};
	}
}
