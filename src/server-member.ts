import { Expose } from 'class-transformer';
import { globalState } from '.';
import { PoliticalParty } from './political-party';
import { BasePoll } from './polls/poll';

/**
 * Used to track the proposal times of each member.
 */
export class ServerMember {
	/** Corresponds to a role ID of their political party */
	@Expose()
	private partyRole: string | null = null;
	@Expose()
	private userID: string;

	constructor(userID: string) {
		this.userID = userID;
	}

	/**
	 * Gets the political party this user is a member of.
	 *
	 * @returns the political party, or null if user is not part of a party.
	 */
	public getPoliticalParty(): PoliticalParty | undefined {
		return this.partyRole ? globalState.getParty(this.partyRole) : undefined;
	}

	/**
	 * Sets the political party this member belongs to.
	 *
	 * @param party political party, or null to leave the party
	 */
	public setPoliticalParty(party?: PoliticalParty | null): void {
		this.partyRole = party ? party.getRole() : null;
	}

	/**
	 * Ensure this person isn't endlessly requesting this poll.
	 * If the member can propose, the cooldown is reset.
	 *
	 * @param poll poll to check
	 * @returns true if the member can propose the poll, false otherwise
	 */
	public canPropose(poll: BasePoll<any>): boolean {
		return globalState.meetsCooldown(this, poll);
	}

	/**
	 * Returns the time remaining in the poll cooldown.
	 *
	 * @param poll poll to check
	 * @returns time remaining in milliseconds before the member can request again
	 */
	public getMillisRemaining(poll: BasePoll<any>): number {
		return globalState.getMillisRemaining(this, poll);
	}

	/**
	 * @returns Discord user ID
	 */
	public getID(): string {
		return this.userID;
	}
}
