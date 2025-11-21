import { APIBaseInteraction, APIUser, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { PartyEditCommand, ServerMember } from '../../../types';
import { globalState } from '../../..';
import { api, BAD_COLOR_RESPONSE, parseColor } from '../../../utils';
import { PoliticalParty } from '../../../political-party';

export default class extends PartyEditCommand {
	async handleInternal(interaction: APIBaseInteraction<any, any>, sender: ServerMember, party: PoliticalParty): Promise<any> {
		// holy fuck
		const color = parseColor(interaction.data.options[0].options[0].options[0].value);
		if (!color) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: BAD_COLOR_RESPONSE,
				},
			};
		}
		// Change the color
		return api(`/guilds/${globalState.serverData.serverID}/roles/${party.getRoleID()}`, {
			method: 'PATCH',
			body: {
				colors: {
					primary_color: color,
					secondary_color: null,
					tertiary_color: null,
				},
			},
		}).then(() => {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: 'Changed party color',
				},
			};
		});
	}
}
