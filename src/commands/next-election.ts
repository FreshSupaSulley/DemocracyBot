import { APIApplicationCommandInteraction, InteractionResponseType } from 'discord-api-types/v10';
import { BaseCommand } from './command';
import { PRESIDENTIAL_VOTE_TIME } from '../utils';

export default class NextElection extends BaseCommand {
	data = { name: 'next-election', options: [], description: 'Returns next election time', type: 1 };

	async handle(interaction: APIApplicationCommandInteraction): Promise<any> {
		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				content: this.isPresidentialVoteActive()
					? `The current election will end at **${this.getExactTime(Date.now() + this.millisRemainingInTerm())} EST**.`
					: `The next election opens on **${this.getExactTime(Date.now() + this.millisRemainingInTerm() - PRESIDENTIAL_VOTE_TIME)} EST**.`,
			},
		};
	}
}
