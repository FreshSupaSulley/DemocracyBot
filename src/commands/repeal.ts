import {
	APIBaseInteraction,
	APIInteractionResponseChannelMessageWithSource,
	InteractionResponseType,
	MessageFlags,
	RESTPatchAPIChannelMessageJSONBody,
} from 'discord-api-types/v10';
import { BaseCommand } from './command';
import { api } from '../utils';
import RepealPoll from '../polls/repeal-poll';
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
		const text = await globalState.getAmendmentText(number - 1);
		return globalState.beginPoll(interaction, new RepealPoll(number, text)).then(() => {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: 'Poll added! i think',
				},
			};
		});
	}
}
