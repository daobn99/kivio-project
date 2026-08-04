import {
  loginSchema,
  requestOtpSchema,
  verifyOtpSchema,
  completeRegistrationSchema,
} from '@/lib/validations/auth'

describe('loginSchema', () => {
  it('有効な入力はパスする', () => {
    const result = loginSchema.safeParse({ email: 'user@example.com', password: 'password123' })
    expect(result.success).toBe(true)
  })

  it('メールアドレス形式が不正な場合はエラーになる', () => {
    const result = loginSchema.safeParse({ email: 'not-email', password: 'password123' })
    expect(result.success).toBe(false)
    expect(result.error!.issues[0].path).toContain('email')
    expect(result.error!.issues[0].message).toBe('メールアドレスの形式が正しくありません')
  })

  it('パスワード未入力の場合はエラーになる（ログインは8文字制限を持たない）', () => {
    const result = loginSchema.safeParse({ email: 'user@example.com', password: '' })
    expect(result.success).toBe(false)
    expect(result.error!.issues[0].message).toBe('パスワードを入力してください')
  })
})

describe('requestOtpSchema', () => {
  it('有効なメールはパスする', () => {
    expect(requestOtpSchema.safeParse({ email: 'user@example.com' }).success).toBe(true)
  })

  it('不正なメールはエラーになる', () => {
    expect(requestOtpSchema.safeParse({ email: 'bad' }).success).toBe(false)
  })
})

describe('verifyOtpSchema', () => {
  it('6桁の数字はパスする', () => {
    expect(verifyOtpSchema.safeParse({ otp: '123456' }).success).toBe(true)
  })

  it('6桁未満・数字以外はエラーになる', () => {
    expect(verifyOtpSchema.safeParse({ otp: '123' }).success).toBe(false)
    expect(verifyOtpSchema.safeParse({ otp: 'abcdef' }).success).toBe(false)
  })
})

describe('completeRegistrationSchema', () => {
  const valid = {
    password: 'password123',
    passwordConfirm: 'password123',
    displayName: 'テスト太郎',
  }

  it('有効な入力はパスする', () => {
    expect(completeRegistrationSchema.safeParse(valid).success).toBe(true)
  })

  it('パスワードが8文字未満の場合はエラーになる', () => {
    const result = completeRegistrationSchema.safeParse({
      ...valid,
      password: '1234567',
      passwordConfirm: '1234567',
    })
    expect(result.success).toBe(false)
    expect(result.error!.issues[0].path).toContain('password')
  })

  it('パスワードと確認用が一致しない場合はエラーになる', () => {
    const result = completeRegistrationSchema.safeParse({
      ...valid,
      passwordConfirm: 'different1',
    })
    expect(result.success).toBe(false)
    expect(result.error!.issues[0].path).toContain('passwordConfirm')
  })

  it('表示名が未入力の場合はエラーになる', () => {
    const result = completeRegistrationSchema.safeParse({ ...valid, displayName: '' })
    expect(result.success).toBe(false)
    expect(result.error!.issues[0].path).toContain('displayName')
  })
})
