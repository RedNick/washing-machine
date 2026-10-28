import AsyncStorage from '@react-native-async-storage/async-storage';
import { useCallback, useEffect, useState } from 'react';

import { NO_PROGRAM_LIST_CHOICES, toggleFavourite, toggleHidden, type ProgramListChoices } from './organisePrograms';

const CHOICES_STORAGE_KEY = 'programList.choices';

async function loadChoices(): Promise<ProgramListChoices> {
  try {
    const saved = await AsyncStorage.getItem(CHOICES_STORAGE_KEY);
    return saved ? { ...NO_PROGRAM_LIST_CHOICES, ...JSON.parse(saved) } : NO_PROGRAM_LIST_CHOICES;
  } catch {
    return NO_PROGRAM_LIST_CHOICES;
  }
}

/** Your favourite and hidden programs, kept on the phone between launches. */
export function useProgramListChoices(): {
  choices: ProgramListChoices;
  toggleFavourite: (programId: string) => void;
  toggleHidden: (programId: string) => void;
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
    toggleFavourite: useCallback((programId: string) => updateChoices((current) => toggleFavourite(current, programId)), [updateChoices]),
    toggleHidden: useCallback((programId: string) => updateChoices((current) => toggleHidden(current, programId)), [updateChoices]),
  };
}
