import { APIPoll, PollLayoutType, RESTPostAPIChannelMessageJSONBody } from 'discord-api-types/v10';
import { InteractionResponseType } from 'discord-interactions';
import { globalState } from '..';
import { api } from '../utils';

export default abstract class BasePoll {
	// Subclasses are required to fill this
	type!: string;

	// These get filled on firePoll()
	messageID?: string;
	startTime?: number;

	ratio: number;
	minParticipation: number;
	votingCooldown: number;
	question: string;

	constructor(ratio: number, minParticipation: number, votingCooldown: number, question: string) {
		this.ratio = ratio;
		this.minParticipation = minParticipation;
		this.votingCooldown = votingCooldown;
		this.question = question;
	}

	public getVotingCooldown(): number {
		return this.votingCooldown;
	}

	abstract isDuplicate(sample: BasePoll): boolean;
	abstract pollPassed(): Promise<any>;
	// Optional
	pollFailed(): Promise<any> {
		return Promise.resolve();
	}
	async firePoll() {
		// Create the poll object
		const poll: APIPoll = {
			allow_multiselect: false,
			expiry: new Date(Date.now() + 864e5).toISOString(),
			question: {
				text: this.question,
			},
			layout_type: PollLayoutType.Default,
			answers: [
				{
					answer_id: 0,
					poll_media: {
						text: 'Yes',
						emoji: {
							id: null,
							name: '✅',
						},
					},
				},
				{
					answer_id: 0,
					poll_media: {
						text: 'No',
						emoji: {
							id: null,
							name: '🚫',
						},
					},
				},
			],
		};
		// Fire the poll
		return api(`/channels/${globalState.serverData.votingBooth}/messages`, {
			method: 'POST',
			body: {
				poll: poll,
			} as RESTPostAPIChannelMessageJSONBody,
		}).then((response: any) => {
			this.messageID = response.id;
			this.startTime = Date.now();
			// Add this poll to the serverData (and save it)
			globalState.serverData.polls.push(this);
			return {
				type: InteractionResponseType.CHANNEL_MESSAGE_WITH_SOURCE,
				data: {
					content: `Poll added!`,
				},
			};
		});
	}
	endPoll() {}
}
