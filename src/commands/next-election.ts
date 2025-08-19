import { APIApplicationCommandInteraction, InteractionResponseType } from 'discord-api-types/v10';
import { PRESIDENTIAL_VOTE_TIME } from '../utils';
import { BaseCommand } from '../types';
import { globalState } from '..';

export default class extends BaseCommand {
	async handle(interaction: APIApplicationCommandInteraction): Promise<any> {
		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				content: globalState.isPresidentialVoteActive()
					? `The current election will end at **${globalState.getExactTime(Date.now() + globalState.millisRemainingInTerm())} EST**.`
					: `The next election opens on **${globalState.getExactTime(
							Date.now() + globalState.millisRemainingInTerm() - PRESIDENTIAL_VOTE_TIME
					  )} EST**.`,
			},
		};
	}
}
