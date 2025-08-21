import {
	APIBaseInteraction,
	APIInteractionResponseChannelMessageWithSource,
	InteractionResponseType,
	MessageFlags,
} from 'discord-api-types/v10';
import { globalState } from '..';
import { BaseCommand } from '../types';
import { PRESIDENTIAL_VOTE_TIME } from '../utils';
import ImpeachPoll from '../polls/impeach-poll';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>): Promise<any> {
		const poll = new ImpeachPoll(interaction.data.options[0].value);

		if (globalState.millisRemainingInTerm() - PRESIDENTIAL_VOTE_TIME < poll.getVoteTime()) {
			if (globalState.isPresidentialVoteActive()) {
				return {
					type: InteractionResponseType.ChannelMessageWithSource,
					data: {
						flags: MessageFlags.Ephemeral,
						content: `Impeachment disabled. An election is active`,
					},
				} as APIInteractionResponseChannelMessageWithSource;
			} else {
				return {
					type: InteractionResponseType.ChannelMessageWithSource,
					data: {
						flags: MessageFlags.Ephemeral,
						content: `Impeachment disabled. The polls will open soon at **${globalState.getUSTime(
							Date.now() + globalState.millisRemainingInTerm() - PRESIDENTIAL_VOTE_TIME
						)} EST.**`,
					},
				} as APIInteractionResponseChannelMessageWithSource;
			}
		}

		return globalState.beginPoll(interaction, poll);
	}
}
