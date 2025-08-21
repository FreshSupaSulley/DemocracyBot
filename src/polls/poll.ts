import { APIMessage, APIPoll, PollLayoutType, RESTPostAPIChannelMessageJSONBody } from 'discord-api-types/v10';
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
	question: string;

	constructor(type: string, ratio: number, minParticipation: number, votingCooldown: number, question: string) {
		this.type = type;
		this.ratio = ratio;
		this.minParticipation = minParticipation;
		this.votingCooldown = votingCooldown;
		this.question = question;
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

	private passesPoll(numYes: number, numNo: number): boolean {
		// Ignore if min participation wasn't met
		if (numYes + numNo <= this.minParticipation) return false;
		return (numYes * 1) / (numYes + numNo) > this.ratio;
	}

	async endPoll(message: APIMessage) {
		const poll = message.poll!;
		// If no one votes, the answers can be undefined
		const numYes = poll.results?.answer_counts[1]?.count ?? 0;
		const numNo = poll.results?.answer_counts[2]?.count ?? 0;
		console.log(`To decide: Yes = ${numYes}, No = ${numNo}`);
		// Delete the message
		await api(`channels/${message.channel_id}/messages/${message.id}`, {
			method: 'DELETE',
		});
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
		await api(`/channels/${globalState.serverData.voteProposal}/messages`, {
			method: 'POST',
			body: {
				content: response,
			},
		});
	}
}
