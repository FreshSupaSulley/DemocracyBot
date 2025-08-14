import { APIApplicationCommandInteraction, InteractionResponseType } from 'discord-api-types/v10';
import { BaseCommand } from './command';
import { PRESIDENTIAL_VOTE_TIME } from '../utils';

export default class extends BaseCommand {
	async handle(interaction: APIApplicationCommandInteraction): Promise<any> {
		console.log(interaction);
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
