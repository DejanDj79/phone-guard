export async function requireParentUserId(
  req: Request,
  ctx: { supabaseAdmin: any },
): Promise<string | null> {
  const authorization = req.headers.get("authorization")?.trim() ?? "";
  const match = /^Bearer\s+(.+)$/i.exec(authorization);
  const token = match?.[1]?.trim();

  if (!token) return null;

  const {
    data: { user },
    error,
  } = await ctx.supabaseAdmin.auth.getUser(token);

  if (error || !user?.id) return null;
  return user.id;
}
