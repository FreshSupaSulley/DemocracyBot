import { APIBaseInteraction, APIRole, InteractionResponseType, MessageFlags } from 'discord-api-types/v10';
import { BaseCommand } from '../../types';
import { globalState } from '../..';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>): Promise<any> {
		const role = Object.values(interaction.data.resolved.roles)[0] as APIRole;
		if (!globalState.isParty(role)) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `That role isn't a political party`,
				},
			};
		}
		const party = globalState.getParty(role.id)!;
		const members = globalState.getPartyMembers(party);
		let description = `**Party members** (${members.length} total):`;
		for (let i = 0; i < Math.min(15, members.length); i++) {
			description += `\n- <@${members[i].getID()}>`;
		}
		if (members.length > 15) {
			description += `\n... and ${members.length - 15} more`;
		}

		// Now assemble the blacklist (banned members)
		const blacklist = party.getBlacklist();
		if (blacklist.length > 0) {
			description += `\n\n**Banned members** (${blacklist.length} total):`;
			for (let i = 0; i < Math.min(15, blacklist.length); i++) {
				description += `\n- <@${blacklist[i]}>`;
			}
			if (blacklist.length > 15) {
				description += `\n... and ${blacklist.length - 15} more`;
			}
		}

		description += `\n\nLed by <@${party.getLeaderID()}>`;
		// Get the leader user object
		// ig this should be cached but atp im so done
		return {
			type: InteractionResponseType.ChannelMessageWithSource,
			data: {
				embeds: [
					{
						color: role.color,
						description: description,
						title: role.name,
					},
				],
			},
		};
	}
}
