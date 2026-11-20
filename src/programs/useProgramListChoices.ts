import AsyncStorage from '@react-native-async-storage/async-storage';
import { useCallback, useEffect, useState } from 'react';

import { isFabricTypeId, type FabricTypeId } from './fabricTypes';
import {
  NO_PROGRAM_LIST_CHOICES,
  chooseFabric,
  toggleFavourite,
  toggleHidden,
  type ProgramListChoices,
} from './organisePrograms';

const CHOICES_STORAGE_KEY = 'programList.choices';

async function loadChoices(): Promise<ProgramListChoices> {
  try {
    const saved = await AsyncStorage.getItem(CHOICES_STORAGE_KEY);
    if (!saved) return NO_PROGRAM_LIST_CHOICES;
    const loaded = { ...NO_PROGRAM_LIST_CHOICES, ...JSON.parse(saved) };
    // Ignore a fabric this version of the app doesn't know.
    return { ...loaded, fabricFilter: isFabricTypeId(loaded.fabricFilter) ? loaded.fabricFilter : null };
  } catch {
    return NO_PROGRAM_LIST_CHOICES;
  }
}

/** Your favourite and hidden programs and fabric chip, kept on the phone between launches. */
export function useProgramListChoices(): {
  choices: ProgramListChoices;
  toggleFavourite: (programId: string) => void;
  toggleHidden: (programId: string) => void;
  chooseFabric: (fabric: FabricTypeId | null) => void;
} {
  const [choices, setChoices] = useState<ProgramListChoices>(NO_PROGRAM_LIST_CHOICES);

  useEffect(() => {
    void loadChoices().then(setChoices);
  }, []);

  /** Applies a change on screen straight away, then saves it. */
  const updateChoices = useCallback((change: (current: ProgramListChoices) => ProgramListChoices) => {
    setChoices((current) => {
      const updated = change(current);
      AsyncStorage.setItem(CHOICES_STORAGE_KEY, JSON.stringify(updated)).catch(() => {
        // Not saved; it still applies until the app closes.
      });
      return updated;
    });
  }, []);

  return {
    choices,
    toggleFavourite: useCallback(
      (programId: string) => updateChoices((current) => toggleFavourite(current, programId)),
      [updateChoices],
    ),
    toggleHidden: useCallback(
      (programId: string) => updateChoices((current) => toggleHidden(current, programId)),
      [updateChoices],
    ),
    chooseFabric: useCallback(
      (fabric: FabricTypeId | null) => updateChoices((current) => chooseFabric(current, fabric)),
      [updateChoices],
    ),
  };
}
