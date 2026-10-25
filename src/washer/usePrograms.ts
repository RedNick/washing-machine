import { useCallback, useEffect, useState } from 'react';

import { useConnectedWasher } from './WasherContext';
import type { WasherProgram } from './washerTypes';

/** Loads the machine's program list for a screen. */
export function usePrograms(): {
  programs: WasherProgram[] | null;
  loadError: string | null;
  reload: () => void;
} {
  const { washer } = useConnectedWasher();
  const [programs, setPrograms] = useState<WasherProgram[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    let isStillWanted = true;
    setLoadError(null);
    washer
      .getPrograms()
      .then((loadedPrograms) => {
        if (isStillWanted) setPrograms(loadedPrograms);
      })
      .catch((error: unknown) => {
        if (isStillWanted) setLoadError(error instanceof Error ? error.message : String(error));
      });
    return () => {
      isStillWanted = false;
    };
  }, [washer, reloadCount]);

  const reload = useCallback(() => setReloadCount((count) => count + 1), []);
  return { programs, loadError, reload };
}
