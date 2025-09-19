import React, { useState } from 'react';
import {
  Plus,
  Wallet,
  CreditCard,
  DollarSign,
  TrendingUp,
  Settings,
  Eye,
  EyeOff,
  Edit3,
  MoreVertical,
  Banknote,
  ArrowUpRight,
  ArrowDownRight,
  Trash2,
  RefreshCw,
  ArrowLeftRight
} from 'lucide-react';

interface Account {
  id: string;
  name: string;
  type: 'savings' | 'investment' | 'trading';
  currency: 'KRW' | 'USD';
  balance: number;
  isDefault: boolean;
  createdAt: string;
  tradingFee: number; // 거래 수수료 (%)
}

const Account: React.FC = () => {
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showDepositModal, setShowDepositModal] = useState(false);
  const [showTransferModal, setShowTransferModal] = useState(false);
  const [hideBalances, setHideBalances] = useState(false);
  const [newAccountName, setNewAccountName] = useState('');
  const [newAccountType, setNewAccountType] = useState<'savings' | 'investment' | 'trading'>('trading');
  const [newAccountCurrency, setNewAccountCurrency] = useState<'KRW' | 'USD'>('KRW');
  const [editingAccount, setEditingAccount] = useState<Account | null>(null);
  const [selectedAccount, setSelectedAccount] = useState<Account | null>(null);
  const [depositAmount, setDepositAmount] = useState('');
  const [transferAmount, setTransferAmount] = useState('');
  const [transferFrom, setTransferFrom] = useState('');
  const [transferTo, setTransferTo] = useState('');

  // Mock 계좌 데이터
  const [accounts, setAccounts] = useState<Account[]>([
    {
      id: '1',
      name: '주식 투자 계좌',
      type: 'trading',
      currency: 'KRW',
      balance: 1234567,
      isDefault: true,
      createdAt: '2024-01-15',
      tradingFee: 0.25
    },
    {
      id: '2',
      name: 'USD 해외투자',
      type: 'investment',
      currency: 'USD',
      balance: 5420.50,
      isDefault: false,
      createdAt: '2024-02-20',
      tradingFee: 0.15
    },
    {
      id: '3',
      name: '비상금 적금',
      type: 'savings',
      currency: 'KRW',
      balance: 3000000,
      isDefault: false,
      createdAt: '2024-03-10',
      tradingFee: 0.0
    }
  ]);

  const accountTypes = [
    { value: 'trading', label: '모의 거래', icon: TrendingUp, description: '주식 모의 거래용 계좌' },
    { value: 'investment', label: '투자', icon: Wallet, description: '장기 투자용 계좌' },
    { value: 'savings', label: '저축', icon: Banknote, description: '저축 및 적금용 계좌' }
  ];

  const currencies = [
    { value: 'KRW', label: '원화 (KRW)', symbol: '₩' },
    { value: 'USD', label: '달러 (USD)', symbol: '$' }
  ];

  const formatBalance = (balance: number, currency: 'KRW' | 'USD') => {
    if (hideBalances) return currency === 'KRW' ? '₩***,***' : '$***.**';

    const symbol = currency === 'KRW' ? '₩' : '$';
    const formatted = currency === 'KRW'
      ? balance.toLocaleString()
      : balance.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    return `${symbol}${formatted}`;
  };

  const getAccountTypeInfo = (type: string) => {
    return accountTypes.find(t => t.value === type) || accountTypes[0];
  };

  const handleCreateAccount = () => {
    if (!newAccountName.trim()) return;

    const newAccount: Account = {
      id: Date.now().toString(),
      name: newAccountName,
      type: newAccountType,
      currency: newAccountCurrency,
      balance: 0,
      isDefault: accounts.length === 0,
      createdAt: new Date().toISOString().split('T')[0],
      tradingFee: newAccountType === 'trading' ? 0.25 : newAccountType === 'investment' ? 0.15 : 0.0
    };

    setAccounts([...accounts, newAccount]);
    setShowCreateModal(false);
    setNewAccountName('');
    setNewAccountType('trading');
    setNewAccountCurrency('KRW');
  };

  const handleEditAccount = (account: Account) => {
    setEditingAccount(account);
    setShowEditModal(true);
  };

  const handleSaveEdit = () => {
    if (!editingAccount) return;

    setAccounts(accounts.map(acc =>
      acc.id === editingAccount.id ? editingAccount : acc
    ));
    setShowEditModal(false);
    setEditingAccount(null);
  };

  const handleDeleteAccount = (accountId: string) => {
    if (accounts.length <= 1) {
      alert('최소 1개의 계좌는 유지해야 합니다.');
      return;
    }

    if (confirm('정말로 이 계좌를 삭제하시겠습니까?')) {
      setAccounts(accounts.filter(acc => acc.id !== accountId));
    }
  };

  const handleDeposit = () => {
    if (!selectedAccount || !depositAmount || parseFloat(depositAmount) <= 0) return;

    setAccounts(accounts.map(acc =>
      acc.id === selectedAccount.id
        ? { ...acc, balance: acc.balance + parseFloat(depositAmount) }
        : acc
    ));

    setShowDepositModal(false);
    setDepositAmount('');
    setSelectedAccount(null);
  };

  const handleTransfer = () => {
    if (!transferFrom || !transferTo || !transferAmount || parseFloat(transferAmount) <= 0) return;
    if (transferFrom === transferTo) return;

    const fromAccount = accounts.find(acc => acc.id === transferFrom);
    if (!fromAccount || fromAccount.balance < parseFloat(transferAmount)) {
      alert('잔액이 부족합니다.');
      return;
    }

    setAccounts(accounts.map(acc => {
      if (acc.id === transferFrom) {
        return { ...acc, balance: acc.balance - parseFloat(transferAmount) };
      }
      if (acc.id === transferTo) {
        return { ...acc, balance: acc.balance + parseFloat(transferAmount) };
      }
      return acc;
    }));

    setShowTransferModal(false);
    setTransferAmount('');
    setTransferFrom('');
    setTransferTo('');
  };

  const totalBalance = accounts.reduce((sum, account) => {
    // 간단히 KRW 기준으로 환산 (실제로는 환율 적용)
    const krwBalance = account.currency === 'USD' ? account.balance * 1300 : account.balance;
    return sum + krwBalance;
  }, 0);

  return (
    <div className="max-w-7xl mx-auto px-4 py-6">
      <div className="space-y-6">
        {/* 헤더 */}
        <div className="flex flex-col md:flex-row md:items-center md:justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">계좌 관리</h1>
            <p className="text-gray-600 mt-1">투자 및 저축 계좌를 관리하세요</p>
          </div>
          <div className="flex items-center space-x-3 mt-4 md:mt-0">
            <button
              onClick={() => setHideBalances(!hideBalances)}
              className="flex items-center space-x-2 px-4 py-2 bg-gray-100 text-gray-700 rounded-lg hover:bg-gray-200 transition-colors"
            >
              {hideBalances ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              <span>{hideBalances ? '잔액 표시' : '잔액 숨기기'}</span>
            </button>
            <button
              onClick={() => setShowCreateModal(true)}
              className="flex items-center space-x-2 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
            >
              <Plus className="w-4 h-4" />
              <span>계좌 생성</span>
            </button>
          </div>
        </div>

        {/* 총 자산 요약 */}
        <div className="bg-gradient-to-r from-indigo-600 to-purple-600 rounded-xl shadow-sm p-6 text-white">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-indigo-100 text-sm font-medium">총 자산</p>
              <p className="text-3xl font-bold">{formatBalance(totalBalance, 'KRW')}</p>
              <p className="text-indigo-100 text-sm mt-1">{accounts.length}개 계좌</p>
            </div>
            <div className="bg-white/20 p-3 rounded-lg">
              <Wallet className="w-8 h-8" />
            </div>
          </div>
        </div>

        {/* 계좌 목록 */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {accounts.map((account) => {
            const typeInfo = getAccountTypeInfo(account.type);
            const TypeIcon = typeInfo.icon;

            return (
              <div key={account.id} className="bg-white rounded-xl shadow-sm border border-gray-100 p-6 hover:shadow-md transition-shadow">
                <div className="flex items-start justify-between mb-4">
                  <div className="flex items-center space-x-3">
                    <div className="bg-indigo-100 p-2 rounded-lg">
                      <TypeIcon className="w-5 h-5 text-indigo-600" />
                    </div>
                    <div>
                      <h3 className="font-semibold text-gray-900">{account.name}</h3>
                      <p className="text-sm text-gray-500">{typeInfo.label}</p>
                    </div>
                  </div>
                  <div className="flex items-center space-x-1">
                    {account.isDefault && (
                      <span className="bg-green-100 text-green-800 text-xs px-2 py-1 rounded-full">기본</span>
                    )}
                    <button
                      onClick={() => handleEditAccount(account)}
                      className="p-1 text-gray-400 hover:text-gray-600"
                      title="계좌 편집"
                    >
                      <Edit3 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => handleDeleteAccount(account.id)}
                      className="p-1 text-gray-400 hover:text-red-600"
                      title="계좌 삭제"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>

                <div className="space-y-3">
                  <div>
                    <p className="text-sm text-gray-500">잔액</p>
                    <p className="text-2xl font-bold text-gray-900">
                      {formatBalance(account.balance, account.currency)}
                    </p>
                  </div>

                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-500">통화</span>
                    <span className="font-medium">{account.currency}</span>
                  </div>

                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-500">거래 수수료</span>
                    <span className="font-medium">{account.tradingFee}%</span>
                  </div>

                  <div className="flex items-center justify-between text-sm">
                    <span className="text-gray-500">개설일</span>
                    <span className="font-medium">{new Date(account.createdAt).toLocaleDateString('ko-KR')}</span>
                  </div>

                  <div className="flex space-x-2 pt-3 border-t">
                    <button
                      onClick={() => {
                        setSelectedAccount(account);
                        setShowDepositModal(true);
                      }}
                      className="flex-1 flex items-center justify-center space-x-2 py-2 px-3 bg-green-50 text-green-700 rounded-lg hover:bg-green-100 transition-colors"
                    >
                      <ArrowDownRight className="w-4 h-4" />
                      <span className="text-sm font-medium">입금</span>
                    </button>
                    <button
                      onClick={() => setShowTransferModal(true)}
                      className="flex-1 flex items-center justify-center space-x-2 py-2 px-3 bg-blue-50 text-blue-700 rounded-lg hover:bg-blue-100 transition-colors"
                    >
                      <ArrowLeftRight className="w-4 h-4" />
                      <span className="text-sm font-medium">이체</span>
                    </button>
                    <button
                      onClick={() => {
                        if (confirm('계좌를 초기 상태로 리셋하시겠습니까?')) {
                          setAccounts(accounts.map(acc =>
                            acc.id === account.id ? { ...acc, balance: 0 } : acc
                          ));
                        }
                      }}
                      className="p-2 bg-orange-50 text-orange-700 rounded-lg hover:bg-orange-100 transition-colors"
                      title="계좌 리셋"
                    >
                      <RefreshCw className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>

        {/* 빈 상태 */}
        {accounts.length === 0 && (
          <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-12 text-center">
            <Wallet className="w-16 h-16 text-gray-300 mx-auto mb-4" />
            <h3 className="text-lg font-semibold text-gray-900 mb-2">계좌가 없습니다</h3>
            <p className="text-gray-500 mb-6">첫 번째 계좌를 생성하여 투자를 시작해보세요</p>
            <button
              onClick={() => setShowCreateModal(true)}
              className="px-6 py-3 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
            >
              계좌 생성하기
            </button>
          </div>
        )}
      </div>

      {/* 계좌 생성 모달 */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">새 계좌 생성</h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">계좌명</label>
                <input
                  type="text"
                  value={newAccountName}
                  onChange={(e) => setNewAccountName(e.target.value)}
                  placeholder="예: 주식 투자 계좌"
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">계좌 유형</label>
                <div className="space-y-2">
                  {accountTypes.map((type) => {
                    const TypeIcon = type.icon;
                    return (
                      <div
                        key={type.value}
                        onClick={() => setNewAccountType(type.value as any)}
                        className={`p-3 border rounded-lg cursor-pointer transition-colors ${
                          newAccountType === type.value
                            ? 'border-indigo-500 bg-indigo-50'
                            : 'border-gray-200 hover:border-gray-300'
                        }`}
                      >
                        <div className="flex items-center space-x-3">
                          <TypeIcon className="w-5 h-5 text-indigo-600" />
                          <div>
                            <p className="font-medium text-gray-900">{type.label}</p>
                            <p className="text-sm text-gray-500">{type.description}</p>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">통화</label>
                <select
                  value={newAccountCurrency}
                  onChange={(e) => setNewAccountCurrency(e.target.value as 'KRW' | 'USD')}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                >
                  {currencies.map((currency) => (
                    <option key={currency.value} value={currency.value}>
                      {currency.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="flex space-x-3 mt-6">
              <button
                onClick={() => setShowCreateModal(false)}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleCreateAccount}
                disabled={!newAccountName.trim()}
                className="flex-1 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
              >
                생성하기
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 계좌 편집 모달 */}
      {showEditModal && editingAccount && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">계좌 편집</h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">계좌명</label>
                <input
                  type="text"
                  value={editingAccount.name}
                  onChange={(e) => setEditingAccount({ ...editingAccount, name: e.target.value })}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">거래 수수료 (%)</label>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  max="5"
                  value={editingAccount.tradingFee}
                  onChange={(e) => setEditingAccount({ ...editingAccount, tradingFee: parseFloat(e.target.value) || 0 })}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <div className="flex items-center">
                <input
                  type="checkbox"
                  id="isDefault"
                  checked={editingAccount.isDefault}
                  onChange={(e) => setEditingAccount({ ...editingAccount, isDefault: e.target.checked })}
                  className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                />
                <label htmlFor="isDefault" className="ml-2 text-sm text-gray-700">기본 계좌로 설정</label>
              </div>
            </div>

            <div className="flex space-x-3 mt-6">
              <button
                onClick={() => {
                  setShowEditModal(false);
                  setEditingAccount(null);
                }}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleSaveEdit}
                className="flex-1 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 transition-colors"
              >
                저장
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 입금 모달 */}
      {showDepositModal && selectedAccount && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">가상 자금 입금</h3>

            <div className="mb-4 p-3 bg-blue-50 rounded-lg">
              <p className="text-sm text-blue-800">
                <strong>{selectedAccount.name}</strong>에 모의투자용 가상 자금을 추가합니다.
              </p>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">입금 금액</label>
                <input
                  type="number"
                  value={depositAmount}
                  onChange={(e) => setDepositAmount(e.target.value)}
                  placeholder="입금할 금액을 입력하세요"
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
                <p className="text-xs text-gray-500 mt-1">
                  통화: {selectedAccount.currency}
                </p>
              </div>

              <div className="flex space-x-2">
                {[10000, 100000, 1000000].map((amount) => (
                  <button
                    key={amount}
                    onClick={() => setDepositAmount(amount.toString())}
                    className="flex-1 py-2 px-3 bg-gray-100 text-gray-700 rounded-md hover:bg-gray-200 transition-colors text-sm"
                  >
                    {selectedAccount.currency === 'KRW' ? `₩${amount.toLocaleString()}` : `$${amount.toLocaleString()}`}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex space-x-3 mt-6">
              <button
                onClick={() => {
                  setShowDepositModal(false);
                  setDepositAmount('');
                  setSelectedAccount(null);
                }}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleDeposit}
                disabled={!depositAmount || parseFloat(depositAmount) <= 0}
                className="flex-1 px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
              >
                입금하기
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 계좌 이체 모달 */}
      {showTransferModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl p-6 w-full max-w-md mx-4">
            <h3 className="text-lg font-semibold text-gray-900 mb-4">계좌 간 이체</h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">출금 계좌</label>
                <select
                  value={transferFrom}
                  onChange={(e) => setTransferFrom(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                >
                  <option value="">계좌를 선택하세요</option>
                  {accounts.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.name} ({account.currency} {formatBalance(account.balance, account.currency)})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">입금 계좌</label>
                <select
                  value={transferTo}
                  onChange={(e) => setTransferTo(e.target.value)}
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                >
                  <option value="">계좌를 선택하세요</option>
                  {accounts.filter(acc => acc.id !== transferFrom).map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.name} ({account.currency})
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">이체 금액</label>
                <input
                  type="number"
                  value={transferAmount}
                  onChange={(e) => setTransferAmount(e.target.value)}
                  placeholder="이체할 금액을 입력하세요"
                  className="w-full border border-gray-300 rounded-md px-3 py-2 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
                />
                {transferFrom && (
                  <p className="text-xs text-gray-500 mt-1">
                    출금 가능: {formatBalance(accounts.find(acc => acc.id === transferFrom)?.balance || 0, accounts.find(acc => acc.id === transferFrom)?.currency || 'KRW')}
                  </p>
                )}
              </div>
            </div>

            <div className="flex space-x-3 mt-6">
              <button
                onClick={() => {
                  setShowTransferModal(false);
                  setTransferAmount('');
                  setTransferFrom('');
                  setTransferTo('');
                }}
                className="flex-1 px-4 py-2 border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 transition-colors"
              >
                취소
              </button>
              <button
                onClick={handleTransfer}
                disabled={!transferFrom || !transferTo || !transferAmount || parseFloat(transferAmount) <= 0}
                className="flex-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed transition-colors"
              >
                이체하기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Account;