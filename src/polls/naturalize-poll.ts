import { globalState } from '..';
import { APIUser } from 'discord-api-types/v10';
import BasePoll from './poll';
import { InteractionResponseType } from 'discord-interactions';
import { Exclude, Expose } from 'class-transformer';

export default class NaturalizePoll extends BasePoll {
	userID: string;
	// Doesn't seem to do anything but whatever
	@Exclude()
	username: string;

	constructor(userID: string, username?: APIUser) {
		super('naturalize', 0.75, 5, 259200000);
		this.userID = userID;
		this.username = username?.username ?? "if you're reading this I fucked up";
	}

	firePoll() {
		this.question = `Naturalize ${this.username}? They will be able to participate in democracy. If failed, they cannot be renaturalized.`;
		return super.firePoll();
	}

	isDuplicate(sample: NaturalizePoll): boolean {
		return this.userID == sample.userID;
	}

	async pollPassed() {
		return globalState.naturalize(this.userID);
	}

	async pollFailed() {
		return globalState.addToCitizenBlacklist(this.userID);
	}
}
