import { globalState } from '..';
import { APIUser } from 'discord-api-types/v10';
import BasePoll from './poll';

export default class NaturalizePoll extends BasePoll {
	userID: string;

	constructor(user: APIUser) {
		super(
			0.75,
			5,
			259200000,
			`Naturalize ${user.username}? They will be able to participate in democracy. If failed, they cannot be renaturalized.`
		);
		this.userID = user.id;
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
