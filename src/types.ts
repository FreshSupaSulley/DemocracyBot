import { PoliticalParty } from './political-party';
import { BasePoll } from './polls/poll';
import RepealPoll from './polls/repeal-poll';
import { ServerMember } from './server-member';
import { Expose, Type } from 'class-transformer';
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
	amendmentIDs!: string[];
	caqEntries!: { [messageID: string]: string };

	@Type(() => BasePoll, {
		discriminator: {
			property: 'type',
			subTypes: [{ name: 'Repeal', value: RepealPoll }],
		},
	})
	polls!: BasePoll<any>[];
	// Ensures people can't spam polls
	// UserID -> { [poll!: string]!: number; }
	pollCooldownExpiryTimes!: Map<string, Map<string, number>>;

	@Type(() => ServerMember)
	members!: ServerMember[];

	parties!: PoliticalParty[];
	naturalizedCitizens!: number[];
	naturalizationBlacklist!: number[];
	presidentID!: number;
	slogan!: string;
	termEndTime!: number;
	lastTerm!: boolean;
	presidentialVoteMessageID!: number;
	candidates!: [
		{
			slot: number;
			slogan: string;
			userID: number;
			partyRole: number;
		}
	];
}

// Polls
export const POLL_QUESTION_PREFIX = 50;
export const YES_EMOJI = 'U+2705';
export const NO_EMOJI = 'U+1f6ab';
