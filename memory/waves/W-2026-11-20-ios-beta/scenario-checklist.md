# Internal iPhone acceptance

1. If 1.0.3 is installed, capture baseline item/song/routine counts and names without exposing media to external services. Keep the app installed and retain a backup from its existing export flow, if available. If this phone has no prior library, run the upgrade check on a separate disposable library before inviting users with old data.
2. Install the first signed TestFlight build over the existing app. Open catalog, play an old video and a legacy choreography with a repeated element. Confirm IDs/names/order are preserved where observable and no duplicate rows appear after restarting.
3. Import a disposable video copy from Photos and another from Files. Save the first as an element. Build a choreography with the same element twice and confirm the save status. Force quit and reopen, then play both repeats.
4. Delete the disposable source copy from Photos only. Reopen and play the retained element and choreography. Keep the originals and backups.
5. Install a second signed TestFlight build over the first. Verify both the preexisting and newly imported material. Record build numbers, results and any visible errors. Only then send to Client for device acceptance.
