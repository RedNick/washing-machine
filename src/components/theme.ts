import { useColorScheme } from 'react-native';

/** Colours for light and dark mode, picked to follow the phone's setting. */
const lightColours = {
  background: '#F4F6F8',
  card: '#FFFFFF',
  text: '#14202B',
  mutedText: '#5B6773',
  border: '#E1E5EA',
  accent: '#1663D6',
  accentText: '#FFFFFF',
  danger: '#C62828',
  warningBackground: '#FFF4D6',
  warningText: '#6B4E00',
  success: '#2E7D32',
};

const darkColours: typeof lightColours = {
  background: '#0E141A',
  card: '#18212A',
  text: '#EEF2F6',
  mutedText: '#9AA6B2',
  border: '#2A3540',
  accent: '#5C9BFF',
  accentText: '#0E141A',
  danger: '#FF6B6B',
  warningBackground: '#3A3010',
  warningText: '#FFE08A',
  success: '#7BD88F',
};

export type ThemeColours = typeof lightColours;

export function useThemeColours(): ThemeColours {
  return useColorScheme() === 'dark' ? darkColours : lightColours;
}

export const spacing = { small: 8, medium: 16, large: 24 };
