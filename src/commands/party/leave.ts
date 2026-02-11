import { APIBaseInteraction, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { BaseCommand, ServerMember } from '../../types';
import { globalState } from '../..';
import { api } from '../../utils';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>, sender: ServerMember): Promise<any> {
		if (!sender.getPoliticalParty()) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `You're not in a party`,
				},
			};
		}

		if (globalState.isCampaigning(sender)) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `You can't change parties while campaigning`,
				},
			};
		}

		const party = sender.getPoliticalParty();
		// If this is the leader
		if (party?.getLeaderID() == sender.getID()) {
			if (globalState.getPartyMembers(party).length == 1) {
				// Remove from the owner
				sender.setPoliticalParty(null);
				// Remove the party from server data
				const index = globalState.serverData.parties.findIndex((sample) => sample.getID() == party?.getID());
				globalState.serverData.parties.splice(index, 1);
				// Delete the role
				// dont really care to gracefully error check this
				return api(`/guilds/${globalState.serverData.serverID}/roles/${party.getID()}`, {
					method: 'DELETE',
				}).then(() => {
					return {
						type: InteractionResponseType.ChannelMessageWithSource,
						data: {
							content: `Party deleted`,
						},
					};
				});
			}
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Transfer power to one of your disciples first`,
				},
			};
		}
		// Otherwise we are a party member but not the leader
		// Remove the party from this member
		sender.setPoliticalParty(null);
		return api(`/guilds/${globalState.serverData.serverID}/members/${sender.getID()}/roles/${party?.getID()}`, {
			method: 'DELETE',
		}).then(() => {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Left <@&${party?.getID()}>`,
				},
			};
		});
	}
}
