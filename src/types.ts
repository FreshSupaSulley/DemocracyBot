import { plainToClass, Transform, Type } from 'class-transformer';
import { APIBaseInteraction, APIPoll, PollLayoutType, RESTPostAPIChannelMessageJSONBody } from 'discord-api-types/v10';
import { InteractionResponseType } from 'discord-interactions';
import { globalState } from '.';
import { PoliticalParty } from './political-party';
import { api } from './utils';
import NaturalizePoll from './polls/naturalize-poll';
import ProposePoll from './polls/propose-poll';
import RepealPoll from './polls/repeal-poll';
import BasePoll from './polls/poll';

// Required
import 'reflect-metadata';
import 'es6-shim';

export default class ServerData {
	serverID!: string;
	// Channels
	github!: string;
	constipation!: string;
	amendments!: string;
	commandersAndQueefs!: string;
	votingBooth!: string;
	testChannel!: string;
	// Webhook for updating
	githubWebhookID!: string;
	// Roles
	thePresident!: string;
	citizen!: string;

	presidentialCount!: number;
	amendmentIDs: string[] = [];
	caqEntries: { [messageID: string]: string } = {};

	// looks like this isn't required, AND including it actually crashes shit...
	// idk how this functions without this tbh
	// @Type(() => BasePoll, {
	// 	discriminator: {
	// 		property: 'type',
	// 		subTypes: [
	// 			{ name: 'repeal', value: RepealPoll },
	// 			{ name: 'propose', value: ProposePoll },
	// 			{ name: 'naturalize', value: NaturalizePoll },
	// 		],
	// 	},
	// })
	polls: BasePoll[] = [];
	// Ensures people can't spam polls
	// UserID -> { [poll!: string]!: number; }
	@Transform(
		({ value }) => {
			// Transform plain object -> Map<string, Map<string, number>>
			if (value instanceof Map) return value;

			const outerMap = new Map<string, Map<string, number>>();
			for (const [outerKey, innerObj] of Object.entries(value)) {
				const innerMap = new Map<string, number>();
				for (const [innerKey, num] of Object.entries(innerObj as object)) {
					innerMap.set(innerKey, Number(num));
				}
				outerMap.set(outerKey, innerMap);
			}
			return outerMap;
		},
		{ toClassOnly: true }
	)
	pollCooldownExpiryTimes: Map<string, Map<string, number>> = new Map();

	// this might be required though??
	@Type(() => ServerMember)
	members: ServerMember[] = [];

	parties: PoliticalParty[] = [];
	naturalizedCitizens: string[] = [];
	naturalizationBlacklist: string[] = [];
	presidentID!: number;
	slogan!: string;
	termEndTime!: number;
	lastTerm!: boolean;
	presidentialVoteMessageID!: number;
	candidates: Candidate[] = [];

	// dumb shit fix I found on gh
	@Transform(
		(value) => {
			let map = new Map<number, Amendment>();
			for (let entry of Object.entries(value.value)) map.set(Number(entry[0]), plainToClass(Amendment, entry[1]));
			return map;
		},
		{ toClassOnly: true }
	)
	amendmentCache: Map<number, Amendment> = new Map();
}

export abstract class BaseCommand {
	abstract handle(interaction: APIBaseInteraction<any, any>): Promise<any>;
}

export class ServerMember {
	private userID: string;
	private partyRole: string | null = null;

	constructor(userID: string, partyRole: string | null = null) {
		this.userID = userID;
		this.partyRole = partyRole;
	}

	public getPoliticalParty(): PoliticalParty | undefined {
		return this.partyRole ? globalState.getParty(this.partyRole) : undefined;
	}

	public setPoliticalParty(party?: PoliticalParty | null): void {
		this.partyRole = party ? party.getRole() : null;
	}

	public canPropose(poll: BasePoll): boolean {
		return globalState.meetsCooldown(this, poll);
	}

	public getMillisRemaining(poll: BasePoll): number {
		return globalState.getMillisRemaining(this, poll);
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

	constructor(parent: ServerMember, slot: number, slogan: string) {
		super(parent.getID(), parent.getPartyID());
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
	text!: string;
	expiry!: number;

	constructor(text: string, expiry: number) {
		this.text = text;
		this.expiry = expiry;
	}
}

// Polls
export const POLL_QUESTION_PREFIX = 50;
