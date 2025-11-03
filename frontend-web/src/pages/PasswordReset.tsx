import React, { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import Header from '../components/common/Header';

const PasswordReset: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { resetPassword, isLoading } = useAuth();

  const token = searchParams.get('token');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);

  // 토큰이 없으면 홈으로 리다이렉트
  useEffect(() => {
    if (!token) {
      navigate('/');
    }
  }, [token, navigate]);

  const validatePassword = (password: string): boolean => {
    // 비밀번호 검증: 8자 이상, 영문, 숫자, 특수문자 포함
    const passwordRegex = /^(?=.*[a-zA-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/;
    return passwordRegex.test(password);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    // 유효성 검사
    if (!newPassword || !confirmPassword) {
      setError('모든 필드를 입력해주세요.');
      return;
    }

    if (!validatePassword(newPassword)) {
      setError('비밀번호는 8자 이상이며, 영문, 숫자, 특수문자를 포함해야 합니다.');
      return;
    }

    if (newPassword !== confirmPassword) {
      setError('비밀번호가 일치하지 않습니다.');
      return;
    }

    try {
      await resetPassword(token!, newPassword);
      setSuccess(true);
      // 3초 후 로그인 페이지로 이동
      setTimeout(() => {
        navigate('/');
      }, 3000);
    } catch (err: any) {
      const errorMessage = err.response?.data?.detail || err.response?.data?.message || '비밀번호 재설정에 실패했습니다.';
      setError(errorMessage);
    }
  };

  if (!token) {
    return null;
  }

  if (success) {
    return (
      <div style={{ minHeight: '100vh', backgroundColor: '#f8f9fa' }}>
        <Header />
        <div style={{
          maxWidth: '500px',
          margin: '80px auto',
          padding: '40px',
          backgroundColor: 'white',
          borderRadius: '12px',
          boxShadow: '0 2px 10px rgba(0,0,0,0.1)',
          textAlign: 'center'
        }}>
          <h2 style={{ color: '#28a745', marginBottom: '20px', fontSize: '24px' }}>✓ 비밀번호 재설정 완료</h2>
          <p style={{ color: '#6c757d', fontSize: '16px', lineHeight: '1.6' }}>
            비밀번호가 성공적으로 변경되었습니다.<br />
            3초 후 로그인 페이지로 이동합니다...
          </p>
        </div>
      </div>
    );
  }

  return (
    <div style={{ minHeight: '100vh', backgroundColor: '#f8f9fa' }}>
      <Header />
      <div style={{
        maxWidth: '500px',
        margin: '80px auto',
        padding: '40px',
        backgroundColor: 'white',
        borderRadius: '12px',
        boxShadow: '0 2px 10px rgba(0,0,0,0.1)'
      }}>
        <h2 style={{ marginBottom: '30px', fontSize: '24px', color: '#333', textAlign: 'center' }}>
          비밀번호 재설정
        </h2>

        <form onSubmit={handleSubmit}>
          {/* 이메일 (placeholder로 표시) */}
          <div style={{ marginBottom: '20px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', color: '#495057', fontWeight: '500' }}>
              이메일
            </label>
            <input
              type="email"
              placeholder="재설정하는 이메일 주소"
              disabled
              style={{
                width: '100%',
                padding: '12px',
                fontSize: '14px',
                border: '1px solid #dee2e6',
                borderRadius: '6px',
                backgroundColor: '#e9ecef',
                color: '#6c757d',
                cursor: 'not-allowed'
              }}
            />
          </div>

          {/* 새 비밀번호 */}
          <div style={{ marginBottom: '20px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', color: '#495057', fontWeight: '500' }}>
              새 비밀번호
            </label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="영문, 숫자, 특수문자 포함 8자 이상"
              style={{
                width: '100%',
                padding: '12px',
                fontSize: '14px',
                border: '1px solid #dee2e6',
                borderRadius: '6px',
                transition: 'border-color 0.15s'
              }}
              onFocus={(e) => e.target.style.borderColor = '#80bdff'}
              onBlur={(e) => e.target.style.borderColor = '#dee2e6'}
            />
          </div>

          {/* 비밀번호 확인 */}
          <div style={{ marginBottom: '20px' }}>
            <label style={{ display: 'block', marginBottom: '8px', fontSize: '14px', color: '#495057', fontWeight: '500' }}>
              비밀번호 확인
            </label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="비밀번호를 다시 입력하세요"
              style={{
                width: '100%',
                padding: '12px',
                fontSize: '14px',
                border: '1px solid #dee2e6',
                borderRadius: '6px',
                transition: 'border-color 0.15s'
              }}
              onFocus={(e) => e.target.style.borderColor = '#80bdff'}
              onBlur={(e) => e.target.style.borderColor = '#dee2e6'}
            />
          </div>

          {/* 에러 메시지 */}
          {error && (
            <div style={{
              padding: '12px',
              marginBottom: '20px',
              backgroundColor: '#f8d7da',
              border: '1px solid #f5c6cb',
              borderRadius: '6px',
              color: '#721c24',
              fontSize: '14px'
            }}>
              {error}
            </div>
          )}

          {/* 제출 버튼 */}
          <button
            type="submit"
            disabled={isLoading}
            style={{
              width: '100%',
              padding: '14px',
              fontSize: '16px',
              fontWeight: '600',
              color: 'white',
              backgroundColor: isLoading ? '#6c757d' : '#007bff',
              border: 'none',
              borderRadius: '6px',
              cursor: isLoading ? 'not-allowed' : 'pointer',
              transition: 'background-color 0.15s'
            }}
            onMouseEnter={(e) => {
              if (!isLoading) e.currentTarget.style.backgroundColor = '#0056b3';
            }}
            onMouseLeave={(e) => {
              if (!isLoading) e.currentTarget.style.backgroundColor = '#007bff';
            }}
          >
            {isLoading ? '처리 중...' : '비밀번호 재설정'}
          </button>
        </form>

        <div style={{
          marginTop: '20px',
          textAlign: 'center',
          fontSize: '14px',
          color: '#6c757d',
          borderTop: '1px solid #dee2e6',
          paddingTop: '20px'
        }}>
          <p style={{ margin: 0, lineHeight: '1.6' }}>
            링크는 30분 후 만료됩니다.<br />
            링크는 한 번만 사용할 수 있습니다.
          </p>
        </div>
      </div>
    </div>
  );
};

export default PasswordReset;
