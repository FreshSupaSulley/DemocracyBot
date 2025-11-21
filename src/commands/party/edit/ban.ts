import { APIBaseInteraction, APIUser, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { PartyEditCommand, ServerMember } from '../../../types';
import { globalState } from '../../..';
import { api } from '../../../utils';
import { PoliticalParty } from '../../../political-party';

export default class extends PartyEditCommand {
	async handleInternal(interaction: APIBaseInteraction<any, any>, sender: ServerMember, party: PoliticalParty): Promise<any> {
		// First check if the mentioned user is a bot
		const mentioned: APIUser = Object.values(interaction.data.resolved.users)[0] as APIUser;

		// Don't let the owner ban themselves
		if (mentioned.id == sender.getID()) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `You tryna ban yourself??`,
				},
			};
		}

		// No point in banning bots
		if (mentioned.bot) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `How tf is a bot gonna join your party??`,
				},
			};
		}

		// guaranteed to be in a party thanks to super class
		if (party?.addBan(mentioned.id)) {
			const mentionedMember = globalState.getMemberByID(mentioned.id);
			// If they're in this party already, remove them
			if (mentionedMember.getPoliticalParty()?.getRoleID() == party?.getRoleID()) {
				mentionedMember.setPoliticalParty(null);
				// Remove the role too
				api(`/guilds/${globalState.serverData.serverID}/members/${mentionedMember.getID()}/roles/${mentionedMember.getPoliticalParty()}`, {
					method: 'DELETE',
				});
			}
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Banned <@${mentioned.id}> from <@&${party.getRoleID()}>`,
				},
			};
		}
		// They're already banned
		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				content: `<@${mentioned.id}> is already banned`,
			},
		};
	}
}
