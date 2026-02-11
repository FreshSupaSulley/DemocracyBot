import { APIBaseInteraction, APIUser, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { PartyEditCommand, ServerMember } from '../../../types';
import { globalState } from '../../..';
import { PoliticalParty } from '../../../political-party';

export default class extends PartyEditCommand {
	async handleInternal(interaction: APIBaseInteraction<any, any>, sender: ServerMember, party: PoliticalParty): Promise<any> {
		// holy fuck
		const mentioned: APIUser = Object.values(interaction.data.resolved.users)[0] as APIUser;
		if (mentioned.bot) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Bots don't have rights :robot:`,
				},
			};
		}

		// Check if the mentioned user is part of the party
		const mentionedMember = globalState.getMemberByID(mentioned.id);
		if (mentionedMember.getPoliticalParty()?.getID() !== party.getID()) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `That user isn't a member of your party`,
				},
			};
		}

		// No need to transfer it to yourself
		if (mentionedMember.getID() == sender.getID()) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `You're already the leader`,
				},
			};
		}

		// Transfer leadership
		party.setLeaderID(mentioned.id);
		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				content: `Transferred leadership of your party to <@${mentioned.id}>`,
			},
		};
	}
}
