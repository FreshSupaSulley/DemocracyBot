import { APIBaseInteraction, APIGuildMember, APIUser, InteractionResponseType } from 'discord-api-types/v10';
import { globalState } from '..';
import NaturalizePoll from '../polls/naturalize-poll';
import { BaseCommand } from '../types';

export default class extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>): Promise<any> {
		// There is only ever one member mentioned
		// This might break in the future if the decide mentionable can expand to other servers or something prob not
		const resolved = interaction.data.resolved;
		// First check if the mentioned user is a bot
		const member: APIGuildMember = Object.values(resolved.members)[0] as APIGuildMember;
		const user: APIUser = Object.values(resolved.users)[0] as APIUser;
		// Check if it's a bot
		if (user.bot) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `Bots don't have rights :robot:`,
				},
			};
		}

		// Check if we already have the citizen role
		if (globalState.isNaturalized(member, user)) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `This user is already naturalized`,
				},
			};
		}

		if (globalState.isBlacklisted(user.id)) {
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					content: `This user failed naturalization and is permanently in exile`,
				},
			};
		}

		return globalState.beginPoll(interaction, new NaturalizePoll(user.id, user));
	}
}
