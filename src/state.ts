import {
	APIBaseInteraction,
	APIBaseMessage,
	APIGuildMember,
	APIInteractionResponseChannelMessageWithSource,
	APIMessage,
	APIUser,
	InteractionResponseType,
	MessageFlags,
} from 'discord-api-types/v10';
import ServerData, { Amendment, ServerMember } from './types';
import { AMENDMENT_CACHE_TIME, api } from './utils';
import { PoliticalParty } from './political-party';
import { escapeMarkdown } from '@discordjs/formatters';
import BasePoll from './polls/poll';

// Because CF workers can die at any point
export class State {
	env: any;
	serverData: ServerData;

	constructor(env: any, serverData: ServerData) {
		this.env = env;
		this.serverData = serverData;
	}

	getMember(interaction: APIBaseInteraction<any, any>) {
		// our commands for this bot are ALWAYS guild commands so no need to fret
		let id = interaction.member?.user.id!;
		for (let member of this.serverData.members) {
			if (member.getID() == id) {
				return member;
			}
		}
		// If we couldn't find user / server, the ServerMember is new
		const initMember = new ServerMember(id);
		this.serverData.members.push(initMember);
		return initMember;
	}

	getCommandReference(interaction: APIBaseInteraction<any, any>) {
		return `</${interaction.data?.name}:${interaction.data?.id}>`;
	}

	getParty(partyID: string): PoliticalParty | undefined {
		return this.serverData.parties.find((value) => value.role == partyID);
	}

	getMillisRemaining(member: ServerMember, poll: BasePoll): number {
		const memberId = member.getID();
		const cooldowns = this.serverData.pollCooldownExpiryTimes.get(memberId) ?? new Map<string, number>();
		const expiry = cooldowns.get(poll.constructor.name) ?? 0;
		return Math.max(0, expiry - Date.now());
	}

	meetsCooldown(member: ServerMember, poll: BasePoll): boolean {
		// I need this for testing (sike I'm just a tyrant)
		if (member.getID() == this.env.OWNER_ID) return true;
		// Get or create the cooldown map for this member
		const memberId = member.getID();
		const cooldowns = this.serverData.pollCooldownExpiryTimes.get(memberId) ?? new Map<string, number>();

		const pollType = poll.constructor.name;
		const now = Date.now();
		const expiryTime = cooldowns.get(pollType) ?? 0;

		if (now >= expiryTime) {
			cooldowns.set(pollType, now + poll.getVotingCooldown());
			this.serverData.pollCooldownExpiryTimes.set(memberId, cooldowns);
		}

		return false;
	}

	async beginPoll(interaction: APIBaseInteraction<any, any>, poll: BasePoll) {
		// Ensure a duplicate poll doesn't exist
		for (const sample of this.serverData.polls) {
			if (poll.isDuplicate(sample)) {
				return {
					type: InteractionResponseType.ChannelMessageWithSource,
					data: {
						flags: MessageFlags.Ephemeral,
						content: 'Another poll of this kind already exists!',
					},
				} as APIInteractionResponseChannelMessageWithSource;
			}
		}

		// Ensure no one is spamming the poll
		const member = this.getMember(interaction);
		if (!member.canPropose(poll)) {
			const millisLeft = ((member.getMillisRemaining(poll) / 3600000) * 100) / 100;
			return {
				type: InteractionResponseType.ChannelMessageWithSource,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `You cannot ${this.getCommandReference(interaction)} this frequently (${
						millisLeft < 0.1 ? '< 0.1' : millisLeft
					} hr cooldown)`,
				},
			} as APIInteractionResponseChannelMessageWithSource;
		}

		return poll.firePoll();
	}

	isPresidentialVoteActive(): boolean {
		return !!this.serverData.presidentialVoteMessageID;
	}

	millisRemainingInTerm(): number {
		return Math.max(0, this.serverData.termEndTime - Date.now());
	}

	getExactTime(time: number) {
		return new Date(time).toLocaleString('en-US', {
			month: '2-digit',
			day: '2-digit',
			year: 'numeric',
			hour: 'numeric',
			minute: '2-digit',
			hour12: true,
		});
	}

	getTotalAmendments() {
		return this.serverData.amendmentIDs.length;
	}

	async getAmendmentText(index: number) {
		// Check if it's in cache and not expired
		const cached: Amendment | undefined = this.serverData.amendmentCache.get(index);
		const now = Date.now();
		if (!cached || now > cached.expiry) {
			console.log(`Fetching amendment index ${index}`);
			const message = (await api(`channels/${this.serverData.amendments}/messages/${this.serverData.amendmentIDs[index]}`)) as APIMessage;
			const content = message.content;
			// Store in cache
			this.serverData.amendmentCache.set(index, new Amendment(content, now + AMENDMENT_CACHE_TIME));
		}

		// Return cached text
		return this.serverData.amendmentCache.get(index)!.text;
	}

	isNaturalized(member: APIGuildMember, user: APIUser) {
		// If they have the citizen role
		if (member.roles.some((role) => role == this.serverData.citizen)) {
			// Also ensure they're in naturalized set
			if (!this.serverData.naturalizedCitizens.includes(user.id)) {
				this.serverData.naturalizedCitizens.push(user.id);
				console.log(`${member} is a citizen but wasn't in the naturalization list`);
			}
			return true;
		}
		// If they're in the set but don't have the role
		// no need for else here but whatever
		else if (this.serverData.naturalizedCitizens.includes(user.id)) {
			this.naturalize(user.id);
			return true;
		}

		return false;
	}

	naturalize(userID: string) {
		if (this.isBlacklisted(userID)) {
			throw new Error("This member is on the naturalization blacklist! This should've been checked before invoking this method");
		}

		// If we didn't already have them naturalized
		if (!this.serverData.naturalizedCitizens.includes(userID)) {
			this.serverData.naturalizedCitizens.push(userID);
		}

		// Adds the role to the user
		// ideally the outside code will check if they already have this role (it probably does)
		return api(`/guilds/${this.serverData.serverID}/members/${userID}/roles/${this.serverData.citizen}`);
	}

	isBlacklisted(userID: string) {
		return this.serverData.naturalizationBlacklist.includes(userID);
	}

	addToCitizenBlacklist(userID: string) {
		if (this.serverData.naturalizationBlacklist.includes(userID)) {
			throw new Error('User is already on the naturalization blacklist');
		}
		this.serverData.naturalizationBlacklist.push(userID);
	}

	addAmendment(content: string) {
		api(`/channels/${this.serverData.amendments}/messages`, {
			method: 'POST',
			body: {
				content: `**Amendment #${this.getTotalAmendments() + 1}** - ${escapeMarkdown(content)}`,
			},
		}).then((success) => {
			console.log(`Added amendment ${content}`);
			this.serverData.amendmentIDs.push((success as APIBaseMessage).id);
			this.serverData.amendmentCache.set(
				this.serverData.amendmentIDs.length - 1,
				new Amendment(content, Date.now() + AMENDMENT_CACHE_TIME)
			);
		});
	}
}
