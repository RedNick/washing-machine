import { Tabs } from 'expo-router';
import { Text } from 'react-native';

import { useThemeColours } from '../../components/theme';

/** Simple emoji icons, so the app needs no icon font. */
function TabIcon({ emoji, focused }: { emoji: string; focused: boolean }) {
  return <Text style={{ fontSize: 20, opacity: focused ? 1 : 0.5 }}>{emoji}</Text>;
}

export default function TabsLayout() {
  const colours = useThemeColours();
  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: colours.card },
        headerTintColor: colours.text,
        tabBarStyle: { backgroundColor: colours.card, borderTopColor: colours.border },
        tabBarActiveTintColor: colours.accent,
        tabBarInactiveTintColor: colours.mutedText,
        sceneStyle: { backgroundColor: colours.background },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{ title: 'Now', tabBarIcon: ({ focused }) => <TabIcon emoji="🧺" focused={focused} /> }}
      />
      <Tabs.Screen
        name="programs"
        options={{ title: 'Programs', tabBarIcon: ({ focused }) => <TabIcon emoji="📋" focused={focused} /> }}
      />
      <Tabs.Screen
        name="history"
        options={{ title: 'History', tabBarIcon: ({ focused }) => <TabIcon emoji="📊" focused={focused} /> }}
      />
    </Tabs>
  );
}
