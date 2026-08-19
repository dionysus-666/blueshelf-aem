import { revalidatePath, revalidateTag } from 'next/cache';
import { NextRequest, NextResponse } from 'next/server';
import { SITE_ROOT, toRoute } from '@/lib/aem';

/**
 * On-demand ISR: POST /api/revalidate?secret=...&path=/content/blueshelf/us/en/tvs
 * Called when content is published (AEM: a "flush agent" / Adobe I/O event → your frontend). Until then ISR
 * falls back to the 60s revalidate window.
 */
export async function POST(req: NextRequest) {
  const secret = req.nextUrl.searchParams.get('secret');
  if (secret !== process.env.REVALIDATE_SECRET) return NextResponse.json({ ok: false }, { status: 401 });
  const path = req.nextUrl.searchParams.get('path') || SITE_ROOT;
  revalidateTag(path, 'max');
  revalidatePath(toRoute(path) === '#' ? '/' : toRoute(path));
  revalidatePath('/', 'layout'); // navigation lives in the layout
  return NextResponse.json({ ok: true, revalidated: path });
}
