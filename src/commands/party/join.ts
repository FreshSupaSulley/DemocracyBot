import { APIBaseInteraction, APIRole, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { BaseCommand, ServerMember } from '../../types';
import { globalState } from '../..';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>, sender: ServerMember): Promise<any> {
		const role = Object.values(interaction.data.resolved.roles)[0] as APIRole;

		if (!!sender.getPoliticalParty()) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: sender.getPoliticalParty()?.getRoleID() == role.id ? "You're already in this party" : 'You already belong to a party',
				},
			};
		}

		const party = globalState.getParty(role.id);
		if (!party) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `That role isn't a political party`,
				},
			};
		}

		// Check if we're banned
		if (party.isBanned(sender.getID())) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `You're banned from <@&${party.getRoleID()}>`,
				},
			};
		}

		// Add the role
		return globalState.addRoleToMember(sender.getID(), role.id).then(() => {
			// Switch the user's party when successful
			sender.setPoliticalParty(party);
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Joined <@&${role.id}>`,
					// Do not notify anyone that you joined ig
					allowed_mentions: {
						parse: [],
					},
				},
			};
		});
	}
}
