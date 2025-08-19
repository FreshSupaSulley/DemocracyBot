import {
	APIBaseInteraction,
	APIBaseMessage,
	APIEmbed,
	APIGuildMember,
	APIInteractionResponseChannelMessageWithSource,
	APIMessage,
	APIReaction,
	APIRole,
	APIUser,
	InteractionResponseType,
	MessageFlags,
	RESTPostAPIChannelMessageJSONBody,
} from 'discord-api-types/v10';
import ServerData, { Amendment, Candidate, CAQEntry, ServerMember } from './types';
import { AMENDMENT_CACHE_TIME, api, CAQ_UPDATE_TIME, PRESIDENTIAL_VOTE_TIME, TERM_LENGTH } from './utils';
import { PoliticalParty } from './political-party';
import { escapeMarkdown } from '@discordjs/formatters';
import BasePoll from './polls/poll';
import { globalState } from '.';

// Because CF workers can die at any point
export class State {
	env: any;
	serverData: ServerData;

	constructor(env: any, serverData: ServerData) {
		this.env = env;
		this.serverData = serverData;
	}

	getMember(interaction: APIBaseInteraction<any, any>): ServerMember {
		// our commands for this bot are ALWAYS guild commands so no need to fret
		return this.getMemberByID(interaction.member?.user.id!);
	}

	getMemberByID(id: string): ServerMember {
		// our commands for this bot are ALWAYS guild commands so no need to fret
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

	// any because it's Snowflakes returned for IDs
	getParty(partyID: any): PoliticalParty | undefined {
		return this.serverData.parties.find((value) => value.getRoleID() == partyID);
	}

	isParty(role: APIRole): boolean {
		return !!this.getParty(role.id);
	}

	getPartyMembers(party: PoliticalParty) {
		return this.serverData.members.filter((member) => member.getPartyID() === party.getRoleID());
	}

	async createParty(name: string, color: number, leader: string): Promise<PoliticalParty> {
		const response = await api(`guilds/${this.serverData.serverID}/roles`, {
			method: 'POST',
			body: {
				name,
				colors: {
					primary_color: color,
					secondary_color: null,
					tertiary_color: null,
				},
			},
		});
		const role = response as APIRole;
		const party = new PoliticalParty(role.id, leader);
		this.serverData.parties.push(party);
		return party;
	}

	isCampaigning(member: ServerMember) {
		return this.serverData.candidates.some((sample) => sample.getID() == member.getID());
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

	beginPoll(interaction: APIBaseInteraction<any, any>, poll: BasePoll) {
		// Ensure a duplicate poll doesn't exist
		for (const sample of this.serverData.polls) {
			// Check if these polls are the same subclass first
			if (sample.type == poll.type && poll.isDuplicate(sample)) {
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

	async getDiscordMember(userID: string): Promise<APIGuildMember> {
		return (await api(`guilds/${this.serverData.serverID}/members/${userID}`)) as APIGuildMember;
	}

	async getPresidentDiscordMember(): Promise<APIGuildMember> {
		return this.getDiscordMember(this.serverData.presidentID);
	}

	async impeach() {
		console.log('Impeaching President');

		await this.updateCAQ(this.serverData.caqEntries.length - 1, (embed) => {
			// Take advantage of our transformer to modify the footer
			embed.footer!.text = `${embed.footer!.text.substring(embed.footer!.text.indexOf('-') + 2)} impeached ${globalState.getExactTime(
				Date.now()
			)}`;
			return embed;
		});

		// Remove presidential role
		await api(`guilds/${this.serverData.serverID}/members/${this.serverData.presidentID}/roles/${this.serverData.thePresidentRole}`, {
			method: 'DELETE',
		});

		// Reset data
		this.serverData.presidentID = '0';
		this.serverData.termEndTime = Date.now();
		this.serverData.slogan = ''; // doesn't do anything but i like to keep things clean
		this.serverData.lastTerm = false;
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
		return this.addRoleToMember(userID, this.serverData.citizen);
	}

	addRoleToMember(userID: string, roleID: string) {
		return api(`guilds/${this.serverData.serverID}/members/${userID}/roles/${roleID}`, {
			method: 'PUT',
		});
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
		api(`channels/${this.serverData.amendments}/messages`, {
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

	hasPresident(): boolean {
		return this.serverData.presidentID != '0';
	}

	isLastTerm(): boolean {
		// Amendment 2
		return false;
		// return lastTerm;
	}

	getPresidentialCount() {
		return this.serverData.presidentialCount;
	}

	ordinal(i: number): string {
		const suffixes = ['th', 'st', 'nd', 'rd', 'th', 'th', 'th', 'th', 'th', 'th'];
		const mod100 = i % 100;
		if (mod100 === 11 || mod100 === 12 || mod100 === 13) {
			return `${i}th`;
		}
		return `${i}${suffixes[i % 10]}`;
	}

	buildPresidentialVote() {
		let description =
			"@everyone it's time. By the power of the people and the Magna Farta, we will elect our next monthly President that represents the core of this nation's beliefs and thereby representing the people. Cast your vote below:\n";
		this.serverData.candidates.forEach((candidate) => {
			description += `\n**#${candidate.getSlot() + 1}: <@${candidate.getID()}>** (<@&${
				candidate.getPoliticalParty()?.getRoleID() || this.serverData.thePresidentRole
			}>) - *"${candidate.getSlogan()}"*`;
		});

		if (this.serverData.candidates.length == 0) {
			description += '\n*There are no active presidential candidates. Run for office with /campaign.*';
		}

		return api(`channels/${this.serverData.votingBooth}/messages`, {
			method: 'POST',
			body: {
				embeds: [
					{
						title: `${this.ordinal(this.getPresidentialCount() + 1)} Presidential Election`,
						description,
						image: 'https://cdn.discordapp.com/app-icons/910579031391498330/c65afb3995baa1c31212e43f1f643e7e.png',
						color: 16711680, // red
						footer: {
							text: `Vote will be decided in ${
								PRESIDENTIAL_VOTE_TIME / 3.6e6
							} hours. Thank you for being an active participant in our perfect society.`,
						},
					},
				],
			},
		});
	}

	updatePresidentialVote() {
		return api(`channels/${this.serverData.votingBooth}/messages/${this.serverData.presidentialVoteMessageID}`, {
			method: 'PATCH',
			body: this.buildPresidentialVote(),
		});
	}

	unicodeToEmoji(unicodeStr: string) {
		const codePoints = unicodeStr.match(/U\+([0-9a-fA-F]+)/g)!.map((cp) => parseInt(cp.replace('U+', ''), 16));
		return String.fromCodePoint(...codePoints);
	}

	slotToReaction(slot: number): string {
		return this.unicodeToEmoji(slot == 9 ? 'U+1f51f' : 'U+3' + (slot + 1) + 'U+fe0fU+20e3');
	}

	async tick() {
		// If we should update CAQ
		if (Date.now() - this.serverData.lastCAQTime >= CAQ_UPDATE_TIME) {
			this.serverData.lastCAQTime = Date.now();
			// Wrap around when necessary
			this.serverData.lastCAQMember = (this.serverData.lastCAQMember + 1) % this.serverData.caqEntries.length;
			console.debug('Updating CAQ with ID:', this.serverData.lastCAQMember);
			this.updateCAQ(this.serverData.lastCAQMember);
		}

		// If we're voting for President
		if (this.serverData.presidentialVoteMessageID != '0' || this.millisRemainingInTerm() < PRESIDENTIAL_VOTE_TIME) {
			// If a poll needs to be created
			if (this.serverData.presidentialVoteMessageID == '0') {
				console.log('Opening up Presidential vote');
				// Add President as a re-election
				if (this.hasPresident() && !this.isLastTerm()) {
					// Always the first slot, 0
					// Because of the conditional we should be guaranteed to find a president
					this.serverData.candidates.push(new Candidate(this.getMemberByID(this.serverData.presidentID), 0, this.serverData.slogan));
				}

				// Create vote
				const response = (await api(`channels/${this.serverData.votingBooth}/messages`)) as APIMessage;
				this.serverData.presidentialVoteMessageID = response.id;
				this.serverData.presidentialVoteTimeCreated = Date.now();
				// Add first reaction (President re-election)
				if (this.serverData.candidates.length > 0) {
					await api(
						`channels/${this.serverData.votingBooth}/messages/${this.serverData.presidentialVoteMessageID}/reactions/${this.unicodeToEmoji(
							'U+31U+fe0fU+20e3'
						)}/@me`,
						{
							method: 'PUT',
						}
					);
				}
			}
			// Tick vote if already created
			else {
				// If the election is over
				if (Date.now() - this.serverData.presidentialVoteTimeCreated > PRESIDENTIAL_VOTE_TIME) {
					// Each tick when we can decide the winner, keep checking if we have new candidates
					// This could be empty for a while if no one runs
					// But if we were to add a dropping feature this should handle it (along with candidates leaving the server, which should follow the same system)
					if (this.serverData.candidates.length > 0) {
						// Tally votes
						let votes: number[] = [10];

						// Update message to get reactions
						const presidentialVote = (await api(
							`channels/${this.serverData.votingBooth}/messages/${this.serverData.presidentialVoteMessageID}`
						)) as APIMessage;

						for (const r of presidentialVote.reactions!) {
							const reaction: APIReaction = r;
							// Inefficient way to add votes because fuck you
							for (let i = 0; i < 10; i++) {
								const unicode = this.slotToReaction(i);
								if (unicode === r.emoji.name) {
									votes[i] = reaction.count_details.normal;
								}
							}
						}

						let tiedCandidates: Candidate[] = [];
						const maxVotes = Math.max(...votes);

						console.log('Counting presidential votes');

						// For each candidate, add the top ones to the array
						for (let i = 0; i < this.serverData.candidates.length; i++) {
							const candidate = this.serverData.candidates[i];
							console.log(candidate.getID(), votes[i]);

							if (votes[i] == maxVotes) {
								console.log('Adding {} to the tied candidates array', candidate.getID());
								tiedCandidates.push(candidate);
							}
						}

						// Determine if there's a tie. By logic, there must be at least 1
						let nextPresident = tiedCandidates[0];

						if (tiedCandidates.length > 1) {
							console.log('We have a tie!', tiedCandidates);
							nextPresident = tiedCandidates[Math.floor(Math.random() * tiedCandidates.length)];
						}

						// President is elected
						this.serverData.candidates = [];
						console.log(`${nextPresident.getID()} won`);

						// Remove President roll
						if (this.hasPresident()) {
							await api(
								`guilds/${this.serverData.serverID}/members/${this.serverData.presidentID}/roles/${this.serverData.thePresidentRole}`,
								{
									method: 'DELETE',
								}
							);
						}

						// Delete Presidential vote
						await api(`channels/${this.serverData.votingBooth}/messages/${this.serverData.presidentialVoteMessageID}`, {
							method: 'DELETE',
						});

						this.serverData.presidentialVoteMessageID = '0';

						// Transfer power
						if (this.serverData.presidentID == nextPresident.getID()) {
							console.log('Same President:', nextPresident.getID());
							this.serverData.lastTerm = true;
						} else {
							console.log('Elected new president:', nextPresident.getID());
						}

						this.serverData.presidentID = nextPresident.getID();
						this.serverData.slogan = nextPresident.getSlogan();
						this.serverData.termEndTime = Date.now() + TERM_LENGTH;

						// This gets scooped up and deleted by checkMessageForPollResult below
						await api(`channels/${this.serverData.votingBooth}/messages`, {
							method: 'POST',
							body: {
								content: `Welcome <@${nextPresident.getID()}> to The White House!`,
							},
						});

						// Assign the president role to the new guy
						await api(`guilds/${this.serverData.serverID}/members/${nextPresident.getID()}/roles/${this.serverData.thePresidentRole}`, {
							method: 'PUT',
						});

						this.serverData.presidentialCount++;
						const apiMember = await this.getDiscordMember(nextPresident.getID());
						const apiRole = (await api(`guilds/${this.serverData.serverID}/roles/${nextPresident.getID()}`)) as APIRole;

						// Add to commanders and queefs
						const response = (await api(`channels/${this.serverData.votingBooth}/messages`, {
							method: 'POST',
							body: {
								embeds: [
									{
										title: `${this.ordinal(this.getPresidentialCount() + 1)} President of Discordias, **${escapeMarkdown(
											apiMember.user.username
										)}**`,
										description: `<@${nextPresident.getID()}> of **${escapeMarkdown(
											nextPresident.getPoliticalParty()?.getRoleID() || this.serverData.thePresidentRole
										)}**\n\n*"${nextPresident.slogan}"\"*"`,
										image: {
											url: this.getSafeAvatar(apiMember.user),
										},
										color: apiRole.color,
										footer: {
											text: `Served ${this.getExactTime(this.serverData.termEndTime - TERM_LENGTH)} - ${this.getExactTime(
												this.serverData.termEndTime
											)}`,
											icon_url: `https://cdn.discordapp.com/app-icons/910579031391498330/c65afb3995baa1c31212e43f1f643e7e.png`,
										},
									} as APIEmbed,
								],
							},
						})) as APIMessage;

						this.serverData.caqEntries.push(new CAQEntry(nextPresident.getID(), response.id));
						// i used to call update CAQ but ig we not doing that anymore
					}
				}
			}
		}
	}

	async updateCAQ(index: number, updater: (embed: APIEmbed) => APIEmbed = (e) => e) {
		if (index < 0 || index >= this.serverData.caqEntries.length)
			throw new Error(`Index ${index} is out of bounds for CAQ entries length ${this.serverData.caqEntries.length}`);

		const entry = this.serverData.caqEntries[index];
		const url = `channels/${this.serverData.commandersAndQueefs}/messages/${entry.messageID}`;
		// We need both the CAQ messsage object AND the discord user
		const caqMessage = await api(url);
		const [message_2, member] = await Promise.all([caqMessage, this.getDiscordMember(entry.user)]);
		// Now modify the embed
		let embed = message_2.embeds[0];
		// Update title in case they changed their username
		embed.title = `${this.ordinal(this.getPresidentialCount() + 1)} President of Discordias, **${escapeMarkdown(member.user.username)}**`;
		embed.image = {
			url: this.getSafeAvatar(member.user),
		};
		// Apply any additional changes, if any
		embed = updater(embed);
		return await api(url, {
			method: 'PATCH',
			body: {
				embeds: [embed],
			},
		});
	}

	getSafeAvatar(user: APIUser): string {
		return user.avatar
			? `https://cdn.discordapp.com/avatars/${user.id}/${user.avatar}.${user.avatar?.startsWith('a_') ? 'gif' : 'png'}`
			: `https://cdn.discordapp.com/embed/avatars/${Number(BigInt(user.id) >> 22n) % 6}.png`;
	}

	getNextCandidateSlot(): number {
		for (let slot = 0; slot < 10; slot++) {
			if (!this.serverData.candidates.some((c) => c.slot === slot)) {
				return slot;
			}
		}
		throw new Error('Tried to get the next available candidate slot when all slots are taken');
	}
}
