import {
	APIBaseInteraction,
	APIInteractionResponseChannelMessageWithSource,
	InteractionResponseType,
	MessageFlags,
} from 'discord-api-types/v10';
import { BaseCommand } from './command';
import { globalState } from '..';

export default class extends BaseCommand {
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
				content: raw,
			},
		};
	}
}
