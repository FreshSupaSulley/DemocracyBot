import {
	APIBaseInteraction,
	APIInteractionResponseChannelMessageWithSource,
	APIMessage,
	InteractionResponseType,
	MessageFlags,
} from 'discord-api-types/v10';
import ServerData from './types';
import { BasePoll } from './polls/poll';
import { AMENDMENT_CACHE_TIME, api } from './utils';
import { PoliticalParty } from './political-party';
import { ServerMember } from './server-member';

// Because CF workers can die at any point
export class State {
	env: any;
	serverData: ServerData;
	// These are gonna have to get moved to serverData. You know why (this assumes there's a state lol)
	amendmentCache: Map<number, { text: string; expiry: number }> = new Map();

	constructor(env: any, serverData: ServerData) {
		this.env = env;
		this.serverData = serverData;
	}

	getMember(interaction: APIBaseInteraction<any, any>) {
		// our commands for this bot are ALWAYS guild commands so no need to fret
		let id = interaction.member?.user.id!;
		for (let member of this.serverData.members) {
			console.log("CHECKING", (member as ServerMember).getID());
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

	getMillisRemaining(member: ServerMember, poll: BasePoll<any>): number {
		const memberId = member.getID();
		const cooldowns = this.serverData.pollCooldownExpiryTimes.get(memberId) ?? new Map<string, number>();
		const pollType = poll.constructor.name;
		const expiry = cooldowns.get(pollType) ?? 0;
		return Math.max(0, expiry - Date.now());
	}

	meetsCooldown(member: ServerMember, poll: BasePoll<any>): boolean {
		// I need this for testing (sike I'm just a tyrant)
		if (member.getID() == this.env.OWNER_ID) return true;
		// Get or create the cooldown map for this member
		const memberId = member.getID();
		const cooldowns = this.serverData.pollCooldownExpiryTimes.get(memberId) ?? new Map<string, number>();
		this.serverData.pollCooldownExpiryTimes.set(memberId, cooldowns);

		const pollType = poll.constructor.name;
		const now = Date.now();
		const expiryTime = cooldowns.get(pollType) ?? 0;

		if (now >= expiryTime) {
			cooldowns.set(pollType, now + poll.getVotingCooldown());
			return true;
		}

		return false;
	}

	async beginPoll(interaction: APIBaseInteraction<any, any>, poll: BasePoll<any>) {
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

		await poll.firePoll();
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
		console.log("HERE", this.serverData)
		return this.serverData.amendmentIDs.length;
	}

	async getAmendmentText(index: number) {
		// Check if it's in cache and not expired
		const cached = this.amendmentCache.get(index);
		const now = Date.now();
		if (!cached || now > cached.expiry) {
			console.log(`Amendment is not in cache, fetching amendment index ${index}`);
			const message = (await api(`channels/${this.serverData.amendments}/messages/${this.serverData.amendmentIDs[index]}`)) as APIMessage;
			const content = message.content;
			// Store in cache
			this.amendmentCache.set(index, {
				text: content,
				expiry: now + AMENDMENT_CACHE_TIME,
			});
		}

		// Return cached text
		return this.amendmentCache.get(index)!.text;
	}
}
