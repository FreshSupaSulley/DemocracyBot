import { APIBaseInteraction, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { BaseCommand, ServerMember } from '../../types';
import { globalState } from '../..';
import { BAD_COLOR_RESPONSE, parseColor } from '../../utils';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>, sender: ServerMember): Promise<any> {
		if (!!sender.getPoliticalParty()) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Leave your political party before creating one`,
				},
			};
		}

		// We used to check if there's a party with that name already but idgaf rn
		// if we were to readd that i would just store the name in the serverData
		const name = interaction.data.options[0].options[0].value;
		const color = parseColor(
			interaction.data.options[0].options[1]?.value ||
				Math.floor(Math.random() * 16777215)
					.toString(16)
					.padStart(6, '0')
		); // generate a default hex if not provided (color is optional)
		if (!color) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: BAD_COLOR_RESPONSE,
				},
			};
		}
		// We're not making channels anymore
		const party = await globalState.createParty(name, color, sender.getID());
		sender.setPoliticalParty(party);
		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				content: `Congratulations! :tada: You are now the proud leader of the <@&${party.getRoleID()}> party!`,
			},
		};
	}
}
