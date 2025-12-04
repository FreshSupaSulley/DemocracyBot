import { APIApplicationCommandInteraction, InteractionResponseType } from 'discord-api-types/v10';
import { PRESIDENTIAL_VOTE_TIME } from '../utils';
import { BaseCommand } from '../types';
import { globalState } from '..';

export default class extends BaseCommand {
	async handle(interaction: APIApplicationCommandInteraction): Promise<any> {
		// DISASTER RECOVERY CODE
		if (interaction.member?.user.id === globalState.env.OWNER_ID) {
			// await globalState.addAmendment("<@&1402465916326907905>s will have the ability to \*\*Manage Expression\*\*s, meaning any naturalized citizen can add emojis and soundboard SFX at will");
			// await globalState.addAmendment("the winner/tied winners of the Christmas wordle will be given be given the special Christmas Spirit role and thus will automatically be on the nice list next year and earn extra presents from Santa Claus");
			// await globalState.addAmendment("zyron cannot participate in the christmas wordle competition. Additionally, if a majority suspects a player of cheating on the christmas wordle, that player will instead be given the naughty lister role");
		}
		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				content: globalState.isPresidentialVoteActive()
					? `The current election will end at **${globalState.getUSTime(Date.now() + globalState.millisRemainingInTerm())} EST**.`
					: `The next election opens on **${globalState.getUSTime(
							Date.now() + globalState.millisRemainingInTerm() - PRESIDENTIAL_VOTE_TIME
					  )} EST**.`,
			},
		};
	}
}
