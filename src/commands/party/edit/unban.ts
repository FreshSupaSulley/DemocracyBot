import { APIBaseInteraction, APIUser, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { PartyEditCommand, ServerMember } from '../../../types';
import { PoliticalParty } from '../../../political-party';

export default class extends PartyEditCommand {
	async handleInternal(interaction: APIBaseInteraction<any, any>, sender: ServerMember, party: PoliticalParty): Promise<any> {
		// First check if the mentioned user is a bot
		const mentioned: APIUser = Object.values(interaction.data.resolved.users)[0] as APIUser;

		// guaranteed to be in a party thanks to super class
		if (party?.removeBan(mentioned.id)) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Unbanned <@${mentioned.id}> from <@&${party.getRoleID()}>`,
				},
			};
		}
		// They're already banned
		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				flags: MessageFlags.Ephemeral,
				content: `<@${mentioned.id}> isn't banned`,
			},
		};
	}
}
