import { plainToClass, Transform, Type } from 'class-transformer';
import { APIBaseInteraction, APIPoll, MessageFlags, PollLayoutType, RESTPostAPIChannelMessageJSONBody } from 'discord-api-types/v10';
import { InteractionResponseType } from 'discord-interactions';
import { globalState } from '.';
import { PoliticalParty } from './political-party';
import { api } from './utils';
import NaturalizePoll from './polls/naturalize-poll';
import ProposePoll from './polls/propose-poll';
import RepealPoll from './polls/repeal-poll';
import BasePoll from './polls/poll';
import ImpeachPoll from './polls/impeach-poll';

// Required
import 'reflect-metadata';
import 'es6-shim';

export default class ServerData {
	serverID!: string;
	// Channels
	constipationChannel!: string;
	amendmentsChannel!: string;
	commandersAndQueefsChannel!: string;
	votingBoothChannel!: string;
	voteProposalChannel!: string;
	testChannel!: string;
	// Roles
	thePresidentRole!: string;
	citizen!: string;

	presidentialCount!: number;

	@Type(() => Amendment)
	amendments: Amendment[] = [];

	@Type(() => CAQEntry)
	caqEntries: CAQEntry[] = [];

	// looks like this isn't required, AND including it actually crashes shit...
	// idk how this functions without this tbh
	@Type(() => BasePoll, {
		discriminator: {
			property: 'type',
			subTypes: [
				{ name: 'repeal', value: RepealPoll },
				{ name: 'propose', value: ProposePoll },
				{ name: 'naturalize', value: NaturalizePoll },
				{ name: 'impeach', value: ImpeachPoll },
			],
		},
	})
	polls: BasePoll[] = [];

	// this might be required though??
	@Type(() => ServerMember)
	members: ServerMember[] = [];

	@Type(() => PoliticalParty)
	parties: PoliticalParty[] = [];
	naturalizedCitizens: string[] = [];
	naturalizationBlacklist: string[] = [];
	presidentID: string = '0';
	slogan: string = '';
	termEndTime: number = 0;
	lastTerm: boolean = false;
	presidentialVoteMessageID: string = '0';
	presidentialVoteTimeCreated: number = 0;

	@Type(() => Candidate)
	candidates: Candidate[] = [];

	// Ticking
	lastCAQMember: number = 0;
	deleteMessagesChannel: string = '0';
}

export class ExpiryTime {
	pollType: string;
	expiryTime: number;

	constructor(pollType: string, expiryTime: number) {
		this.pollType = pollType;
		this.expiryTime = expiryTime;
	}
}

export class CAQEntry {
	user: string;
	messageID: string;

	constructor(user: string, messageID: string) {
		this.user = user;
		this.messageID = messageID;
	}
}

export abstract class BaseCommand {
	abstract handle(interaction: APIBaseInteraction<any, any>, sender: ServerMember): Promise<any>;
}

export abstract class PartyEditCommand extends BaseCommand {
	async handle(interaction: APIBaseInteraction<any, any>, sender: ServerMember) {
		const party = sender.getPoliticalParty();
		if (!party) {
			return {
				type: InteractionResponseType.CHANNEL_MESSAGE_WITH_SOURCE,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `You aren't in a party`,
				},
			};
		}
		if (sender.getID() != party.getLeaderID()) {
			return {
				type: InteractionResponseType.CHANNEL_MESSAGE_WITH_SOURCE,
				data: {
					flags: MessageFlags.Ephemeral,
					content: `You aren't the party leader (<@${party.getLeaderID()}> is)`,
				},
			};
		}
		// We passed the checks. Pass it to command handler
		return this.handleInternal(interaction, sender, party);
	}
	abstract handleInternal(interaction: APIBaseInteraction<any, any>, sender: ServerMember, party: PoliticalParty): Promise<any>;
}

export class ServerMember {
	private userID: string;
	private partyRole: string | null = null;
	@Type(() => ExpiryTime)
	private cooldowns: ExpiryTime[];

	constructor(userID: string, partyRole: string | null = null, cooldowns: ExpiryTime[]) {
		this.userID = userID;
		this.partyRole = partyRole;
		this.cooldowns = cooldowns;
	}

	public getPoliticalParty(): PoliticalParty | undefined {
		return this.partyRole ? globalState.getParty(this.partyRole) : undefined;
	}

	public setPoliticalParty(party?: PoliticalParty | null): void {
		this.partyRole = party ? party.getRoleID() : null;
	}

	public canPropose(poll: BasePoll): boolean {
		const pollType = poll.constructor.name;
		const now = Date.now();
		// Find the cooldown for the specific poll type
		const cooldown = this.cooldowns.find((sample) => sample.pollType === pollType);
		// If there's an active cooldown, check if it has expired
		if (cooldown) {
			if (now < cooldown.expiryTime) {
				// Still within the cooldown period
				return false;
			}
			// If the cooldown has expired, allow proposing
			return true;
		}
		// If no cooldown exists, create one and allow proposing
		const newCooldown = new ExpiryTime(pollType, now + poll.getVotingCooldown());
		this.cooldowns.push(newCooldown);
		return true;
	}

	/**
	 * Gets the number of milliseconds left in this user's cooldown for this particular poll type.
	 * @param poll poll (type) to check
	 * @returns number of milliseconds before the member can propose again
	 */
	public getMillisRemaining(poll: BasePoll): number {
		const cooldown = this.cooldowns.find((sample) => sample.pollType == poll.constructor.name);
		const expiry = cooldown?.expiryTime!;
		return Math.max(0, expiry - Date.now());
	}

	public getID(): string {
		return this.userID;
	}

	public getPartyID(): string | null {
		return this.partyRole;
	}
}

export class Candidate extends ServerMember {
	slot: number;
	slogan: string;

	constructor(id: string, party: string | null, slot: number, slogan: string) {
		super(id, party, []);
		this.slot = slot;
		this.slogan = slogan;
	}

	public getSlot(): number {
		return this.slot;
	}

	public getSlogan(): string {
		return this.slogan;
	}

	public setSlogan(slogan: string) {
		this.slogan = slogan;
	}
}

export class Amendment {
	id: string;
	content: string;
	repealed?: boolean;

	constructor(id: string, text: string, repealed?: boolean) {
		this.id = id;
		this.content = text;
		this.repealed = repealed;
	}
}

// Polls
export const POLL_QUESTION_PREFIX = 50;
