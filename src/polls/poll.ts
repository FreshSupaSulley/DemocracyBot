import { APIPoll, PollLayoutType, RESTPostAPIChannelMessageJSONBody } from 'discord-api-types/v10';
import ServerData from '../types';
import { api } from '../utils';
import { BaseCommand } from '../commands/command';
import { globalState } from '..';
import { Expose } from 'class-transformer';

export abstract class BasePoll<T extends BasePoll<T>> {
	@Expose()
	type!: string;
	@Expose()
	ratio: number;
	@Expose()
	minParticipation: number;
	@Expose()
	votingCooldown: number;
	@Expose()
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

	abstract isDuplicate(sample: T): boolean;
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
			],
		};
		// Fire the poll
		return api(`/channels/${globalState.serverData.votingBooth}/messages`, {
			method: 'POST',
			body: {
				poll: poll,
			} as RESTPostAPIChannelMessageJSONBody,
		}).then((response) => {
			// Add this poll to the serverData (and save it)
			globalState.serverData.polls.push(this);
		});
	}
	endPoll() {}
}
