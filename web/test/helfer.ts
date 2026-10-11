import { type GesamtPlan, type Lesson, type TagesPlan } from '../src/logic/models';
import { parsePlan } from '../src/logic/parser';

/** Wie im Kotlin-Test: XML-Text → Gesamtplan (Schulnummer "1"). */
export function plan(xml: string): GesamtPlan {
  const p = parsePlan(xml, '1');
  if (!p) throw new Error('Plan nicht lesbar');
  return p;
}

export function lesson(tp: TagesPlan, fach: string): Lesson {
  const l = tp.stunden.find((s) => s.fach === fach);
  if (!l) throw new Error(`Keine Stunde ${fach}`);
  return l;
}
