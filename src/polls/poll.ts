import { APIMessage, APIPoll, APIPollAnswer, PollLayoutType, RESTPostAPIChannelMessageJSONBody } from 'discord-api-types/v10';
import { InteractionResponseType } from 'discord-interactions';
import { globalState } from '..';
import { api } from '../utils';

export default abstract class BasePoll {
	type: string;

	// These get filled on firePoll()
	messageID!: string;
	startTime!: number;

	ratio: number;
	minParticipation: number;
	votingCooldown: number;
	// This is used in the result but not usually filled in the constructor
	question!: string;

	constructor(type: string, ratio: number, minParticipation: number, votingCooldown: number) {
		this.type = type;
		this.ratio = ratio;
		this.minParticipation = minParticipation;
		this.votingCooldown = votingCooldown;
	}

	public getExpiryTime(): number {
		return this.startTime + this.getVoteTime();
	}

	// Every poll (for now) can only have 1 day of voting time
	public getVoteTime(): number {
		return 8.64e7;
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
			expiry: new Date(Date.now() + this.getVoteTime()).toISOString(),
			question: {
				text: this.question,
			},
			layout_type: PollLayoutType.Default,
			answers: [
				{
					answer_id: 1,
					poll_media: {
						text: 'Yes',
						emoji: {
							id: null,
							name: '✅',
						},
					},
				},
				{
					answer_id: 2,
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
		return api(`channels/${globalState.serverData.votingBoothChannel}/messages`, {
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

	private passesPoll(numYes: number, numNo: number): boolean {
		// Ignore if min participation wasn't met
		if (numYes + numNo <= this.minParticipation) return false;
		return (numYes * 1) / (numYes + numNo) > this.ratio;
	}

	/**
	 * Runs actions depending on the result of the poll. Does **NOT** delete the poll message!
	 * @param message original poll as {@link APIMessage}
	 */
	async endPoll(message: APIMessage) {
		const poll = message.poll!;
		console.log('The poll:', JSON.stringify(poll));
		// If no one votes, the answers can be undefined
		const numYes = poll.results?.answer_counts.find((count) => count.id === 1)?.count ?? 0;
		const numNo = poll.results?.answer_counts.find((count) => count.id === 2)?.count ?? 0;
		console.log(`To decide: Yes = ${numYes}, No = ${numNo}`);
		let response;
		// If we passed
		if (this.passesPoll(numYes, numNo)) {
			console.log('Poll passed');
			response = `**${this.question}** passed, with a Yes / No ratio of **${numYes}** / **${numNo}**!`;
			// Run whatever happens when it passes
			await this.pollPassed();
		} else {
			console.log('Poll failed');
			response = `**${this.question}** failed to pass. Needs ${this.minParticipation} voters and ${
				this.ratio * 100
			}% approval (Yes / No ratio: **${numYes}** / **${numNo}**)`;
			await this.pollFailed();
		}
		// Send the poll result in a garbage channel
		await api(`channels/${globalState.serverData.voteProposalChannel}/messages`, {
			method: 'POST',
			body: {
				content: response,
			},
		});
	}
}
